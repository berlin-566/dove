package qr

import (
	"fmt"
	"strings"

	qrcode "github.com/skip2/go-qrcode"
)

// GenerateMatrix generates a boolean 2D matrix representing the QR code for content.
func GenerateMatrix(content string, level qrcode.RecoveryLevel) ([][]bool, error) {
	qr, err := qrcode.New(content, level)
	if err != nil {
		return nil, err
	}
	return qr.Bitmap(), nil
}

// RenderANSI generates an ANSI half-block character string suitable for printing to any terminal.
// Uses upper and lower half block characters (▀, ▄, █, and space) to render 2 vertical modules per terminal row.
func RenderANSI(content string, level qrcode.RecoveryLevel) (string, error) {
	bitmap, err := GenerateMatrix(content, level)
	if err != nil {
		return "", err
	}

	height := len(bitmap)
	if height == 0 {
		return "", fmt.Errorf("empty QR matrix")
	}
	width := len(bitmap[0])

	// Quiet zone: 2 modules
	quiet := 2
	totalWidth := width + (quiet * 2)
	totalHeight := height + (quiet * 2)

	// Expanded grid including quiet zone
	grid := make([][]bool, totalHeight)
	for y := 0; y < totalHeight; y++ {
		grid[y] = make([]bool, totalWidth)
		for x := 0; x < totalWidth; x++ {
			origX := x - quiet
			origY := y - quiet
			if origX >= 0 && origX < width && origY >= 0 && origY < height {
				grid[y][x] = bitmap[origY][origX]
			}
		}
	}

	var sb strings.Builder
	// Process 2 vertical lines per iteration
	for y := 0; y < totalHeight; y += 2 {
		for x := 0; x < totalWidth; x++ {
			top := grid[y][x]
			bottom := false
			if y+1 < totalHeight {
				bottom = grid[y+1][x]
			}

			// In dark terminal: white background, black foreground or inverse
			// true = black module, false = white background
			if top && bottom {
				sb.WriteString("\033[30;40m \033[0m") // both black
			} else if top && !bottom {
				sb.WriteString("\033[30;47m▄\033[0m") // top black, bottom white
			} else if !top && bottom {
				sb.WriteString("\033[30;47m▀\033[0m") // top white, bottom black
			} else {
				sb.WriteString("\033[37;47m \033[0m") // both white
			}
		}
		sb.WriteString("\n")
	}

	return sb.String(), nil
}
