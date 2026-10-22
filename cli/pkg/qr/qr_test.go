package qr

import (
	"strings"
	"testing"

	qrcode "github.com/skip2/go-qrcode"
)

func TestRenderANSI(t *testing.T) {
	rendered, err := RenderANSI("https://github.com/berlin-566/dove", qrcode.Medium)
	if err != nil {
		t.Fatalf("RenderANSI failed: %v", err)
	}

	if len(rendered) == 0 {
		t.Fatalf("expected non-empty rendered ANSI string")
	}

	if !strings.Contains(rendered, "\033[") {
		t.Fatalf("expected ANSI escape sequences in rendered output")
	}
}
