package fountain

import (
	"encoding/binary"
	"errors"
	"hash/crc32"
	"math/rand"
)

const (
	MagicByte1 byte = 0x44 // 'D'
	MagicByte2 byte = 0x56 // 'V'
	HeaderSize      = 20
)

var (
	ErrCorruptFrame     = errors.New("fountain frame failed integrity checksum")
	ErrInvalidMagicByte = errors.New("invalid magic bytes in frame header")
)

// Symbol represents an encoded fountain symbol packet.
type Symbol struct {
	SessionID   uint64
	TotalChunks uint16
	ChunkSize   uint16
	Seed        uint32
	Data        []byte
}

// Encoder produces rateless fountain symbols from raw byte source data.
type Encoder struct {
	SessionID   uint64
	Chunks      [][]byte
	TotalChunks uint16
	ChunkSize   uint16
	CurrentSeed uint32
}

// NewEncoder slices source data into K chunks of fixed chunkSize.
func NewEncoder(sessionID uint64, data []byte, chunkSize int) *Encoder {
	if chunkSize <= 0 {
		chunkSize = 350
	}

	dataLen := len(data)
	totalChunks := (dataLen + chunkSize - 1) / chunkSize
	if totalChunks == 0 {
		totalChunks = 1
	}

	chunks := make([][]byte, totalChunks)
	for i := 0; i < totalChunks; i++ {
		chunks[i] = make([]byte, chunkSize)
		start := i * chunkSize
		end := start + chunkSize
		if end > dataLen {
			end = dataLen
		}
		if start < dataLen {
			copy(chunks[i], data[start:end])
		}
	}

	return &Encoder{
		SessionID:   sessionID,
		Chunks:      chunks,
		TotalChunks: uint16(totalChunks),
		ChunkSize:   uint16(chunkSize),
		CurrentSeed: 1,
	}
}

// NextSymbol generates the next rateless fountain symbol using Luby Transform soliton distribution.
func (e *Encoder) NextSymbol() *Symbol {
	seed := e.CurrentSeed
	e.CurrentSeed++

	r := rand.New(rand.NewSource(int64(seed)))
	indices := pickIndices(r, int(e.TotalChunks))

	combined := make([]byte, e.ChunkSize)
	for _, idx := range indices {
		xorBytes(combined, e.Chunks[idx])
	}

	return &Symbol{
		SessionID:   e.SessionID,
		TotalChunks: e.TotalChunks,
		ChunkSize:   e.ChunkSize,
		Seed:        seed,
		Data:        combined,
	}
}

// Serialize formats the symbol into a compact binary frame with CRC32 checksum.
func (s *Symbol) Serialize() []byte {
	buf := make([]byte, HeaderSize+len(s.Data)+4)
	buf[0] = MagicByte1
	buf[1] = MagicByte2
	binary.BigEndian.PutUint64(buf[2:10], s.SessionID)
	binary.BigEndian.PutUint16(buf[10:12], s.TotalChunks)
	binary.BigEndian.PutUint16(buf[12:14], s.ChunkSize)
	binary.BigEndian.PutUint32(buf[14:18], s.Seed)
	binary.BigEndian.PutUint16(buf[18:20], uint16(len(s.Data)))
	copy(buf[HeaderSize:HeaderSize+len(s.Data)], s.Data)

	checksum := crc32.ChecksumIEEE(buf[:HeaderSize+len(s.Data)])
	binary.BigEndian.PutUint32(buf[HeaderSize+len(s.Data):], checksum)

	return buf
}

// Deserialize parses a raw frame and validates its CRC32 checksum.
func Deserialize(raw []byte) (*Symbol, error) {
	if len(raw) < HeaderSize+4 {
		return nil, ErrCorruptFrame
	}

	if raw[0] != MagicByte1 || raw[1] != MagicByte2 {
		return nil, ErrInvalidMagicByte
	}

	dataLen := len(raw) - 4
	expectedChecksum := binary.BigEndian.Uint32(raw[dataLen:])
	actualChecksum := crc32.ChecksumIEEE(raw[:dataLen])
	if expectedChecksum != actualChecksum {
		return nil, ErrCorruptFrame
	}

	sessionID := binary.BigEndian.Uint64(raw[2:10])
	totalChunks := binary.BigEndian.Uint16(raw[10:12])
	chunkSize := binary.BigEndian.Uint16(raw[12:14])
	seed := binary.BigEndian.Uint32(raw[14:18])
	payloadLen := binary.BigEndian.Uint16(raw[18:20])

	if int(HeaderSize+payloadLen) > dataLen {
		return nil, ErrCorruptFrame
	}

	data := make([]byte, payloadLen)
	copy(data, raw[HeaderSize:HeaderSize+payloadLen])

	return &Symbol{
		SessionID:   sessionID,
		TotalChunks: totalChunks,
		ChunkSize:   chunkSize,
		Seed:        seed,
		Data:        data,
	}, nil
}

// Decoder resolves original chunks from received fountain symbols using belief propagation.
type Decoder struct {
	SessionID       uint64
	TotalChunks     int
	ChunkSize       int
	ResolvedChunks  map[int][]byte
	PendingSymbols  []*SymbolEntry
	ResolvedCount   int
}

type SymbolEntry struct {
	Indices map[int]bool
	Data    []byte
}

// NewDecoder initializes a belief-propagation decoder.
func NewDecoder(sessionID uint64, totalChunks, chunkSize int) *Decoder {
	return &Decoder{
		SessionID:      sessionID,
		TotalChunks:    totalChunks,
		ChunkSize:      chunkSize,
		ResolvedChunks: make(map[int][]byte),
		PendingSymbols: make([]*SymbolEntry, 0),
		ResolvedCount:  0,
	}
}

// AddSymbol processes an incoming symbol packet and peels resolved chunks.
func (d *Decoder) AddSymbol(sym *Symbol) bool {
	if sym.SessionID != d.SessionID {
		return false
	}

	r := rand.New(rand.NewSource(int64(sym.Seed)))
	rawIndices := pickIndices(r, d.TotalChunks)

	entry := &SymbolEntry{
		Indices: make(map[int]bool),
		Data:    make([]byte, len(sym.Data)),
	}
	copy(entry.Data, sym.Data)

	for _, idx := range rawIndices {
		if resolved, ok := d.ResolvedChunks[idx]; ok {
			xorBytes(entry.Data, resolved)
		} else {
			entry.Indices[idx] = true
		}
	}

	if len(entry.Indices) == 0 {
		return d.IsComplete()
	}

	d.PendingSymbols = append(d.PendingSymbols, entry)
	d.peel()

	return d.IsComplete()
}

func (d *Decoder) peel() {
	progress := true
	for progress {
		progress = false
		for i := len(d.PendingSymbols) - 1; i >= 0; i-- {
			entry := d.PendingSymbols[i]
			if len(entry.Indices) == 1 {
				var chunkIdx int
				for idx := range entry.Indices {
					chunkIdx = idx
					break
				}

				resolvedData := make([]byte, len(entry.Data))
				copy(resolvedData, entry.Data)
				d.ResolvedChunks[chunkIdx] = resolvedData
				d.ResolvedCount++

				// Remove this symbol
				d.PendingSymbols = append(d.PendingSymbols[:i], d.PendingSymbols[i+1:]...)
				progress = true

				// XOR resolved data across all other pending symbols
				for _, other := range d.PendingSymbols {
					if other.Indices[chunkIdx] {
						xorBytes(other.Data, resolvedData)
						delete(other.Indices, chunkIdx)
					}
				}
				break
			}
		}
	}
}

// IsComplete returns true when all K chunks have been reconstructed.
func (d *Decoder) IsComplete() bool {
	return d.ResolvedCount >= d.TotalChunks
}

// Reconstruct concatenates the recovered chunks in original order.
func (d *Decoder) Reconstruct(originalLen int) []byte {
	out := make([]byte, 0, d.TotalChunks*d.ChunkSize)
	for i := 0; i < d.TotalChunks; i++ {
		if chunk, ok := d.ResolvedChunks[i]; ok {
			out = append(out, chunk...)
		} else {
			out = append(out, make([]byte, d.ChunkSize)...)
		}
	}
	if originalLen > 0 && originalLen <= len(out) {
		return out[:originalLen]
	}
	return out
}

// pickIndices chooses a subset of chunk indices according to a Soliton distribution.
func pickIndices(r *rand.Rand, k int) []int {
	if k <= 1 {
		return []int{0}
	}

	// Degree distribution: 1 with high probability (1/k), then Soliton degrees
	degree := 1
	p := r.Float64()
	if p > (1.0 / float64(k)) {
		d := int(float64(k) * (1.0 - p))
		if d < 2 {
			d = 2
		}
		if d > k {
			d = k
		}
		degree = d
	}

	perm := r.Perm(k)
	return perm[:degree]
}

func xorBytes(dst, src []byte) {
	n := len(dst)
	if len(src) < n {
		n = len(src)
	}
	for i := 0; i < n; i++ {
		dst[i] ^= src[i]
	}
}
