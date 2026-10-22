package server

import (
	"context"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"
)

// Instance manages the local PHP built-in web server subprocess.
type Instance struct {
	Port       int
	WebDir     string
	Cmd        *exec.Cmd
	CancelFunc context.CancelFunc
}

// FindAvailablePort scans for an unused TCP port on 127.0.0.1.
func FindAvailablePort() (int, error) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	defer listener.Close()
	return listener.Addr().(*net.TCPAddr).Port, nil
}

// LocateWebDir resolves the path to the web/ frontend assets directory.
func LocateWebDir() (string, error) {
	candidates := []string{
		"./web",
		"../web",
		filepath.Join(os.Getenv("HOME"), ".local/share/dove/web"),
		"/usr/local/share/dove/web",
	}

	execPath, err := os.Executable()
	if err == nil {
		execDir := filepath.Dir(execPath)
		candidates = append(candidates,
			filepath.Join(execDir, "web"),
			filepath.Join(execDir, "../web"),
			filepath.Join(execDir, "../share/dove/web"),
		)
	}

	for _, dir := range candidates {
		info, err := os.Stat(filepath.Join(dir, "session.php"))
		if err == nil && !info.IsDir() {
			abs, err := filepath.Abs(dir)
			if err == nil {
				return abs, nil
			}
			return dir, nil
		}
	}

	return "", fmt.Errorf("unable to locate web assets directory (session.php not found)")
}

// Start launches 'php -S 127.0.0.1:<port> -t <webDir>' as a background child process.
func Start(ctx context.Context, port int, webDir string) (*Instance, error) {
	phpBin, err := exec.LookPath("php")
	if err != nil {
		return nil, fmt.Errorf("PHP executable not found in PATH: please install php (e.g. pacman -S php, apt install php, brew install php)")
	}

	serverCtx, cancel := context.WithCancel(ctx)
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	cmd := exec.CommandContext(serverCtx, phpBin, "-S", addr, "-t", webDir)
	cmd.Dir = webDir

	if err := cmd.Start(); err != nil {
		cancel()
		return nil, fmt.Errorf("failed to start local PHP server: %w", err)
	}

	// Wait briefly for server to bind port
	for i := 0; i < 20; i++ {
		conn, err := net.DialTimeout("tcp", addr, 100*time.Millisecond)
		if err == nil {
			conn.Close()
			break
		}
		time.Sleep(50 * time.Millisecond)
	}

	return &Instance{
		Port:       port,
		WebDir:     webDir,
		Cmd:        cmd,
		CancelFunc: cancel,
	}, nil
}

// OpenBrowser opens the specified URL in the user's default browser.
func OpenBrowser(url string) error {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "linux":
		cmd = exec.Command("xdg-open", url)
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default:
		return fmt.Errorf("unsupported platform for auto-opening browser")
	}
	return cmd.Start()
}

// Stop cleanly terminates the PHP server subprocess.
func (i *Instance) Stop() {
	if i.CancelFunc != nil {
		i.CancelFunc()
	}
	if i.Cmd != nil && i.Cmd.Process != nil {
		_ = i.Cmd.Process.Kill()
	}
}
