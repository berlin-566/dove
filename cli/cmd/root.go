package cmd

import (
	"fmt"
	"os"
)

const Version = "1.0.0"

// Execute parses command-line arguments and dispatches to subcommands.
func Execute() {
	if len(os.Args) < 2 {
		printHelp()
		os.Exit(1)
	}

	subcommand := os.Args[1]
	args := os.Args[2:]

	switch subcommand {
	case "send":
		if err := RunSend(args); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
	case "get":
		if err := RunGet(args); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
	case "version", "--version", "-v":
		fmt.Printf("dove version %s\n", Version)
	case "help", "--help", "-h":
		printHelp()
	default:
		fmt.Fprintf(os.Stderr, "Unknown command '%s'\n\n", subcommand)
		printHelp()
		os.Exit(1)
	}
}

func printHelp() {
	fmt.Print(`Dove: Air-gapped zero-network optical file sharing system.

Usage:
  dove send <file> [flags]   Broadcast file optically via animated QR codes
  dove get [flags]           Receive file optically using webcam scanner
  dove version               Show version information
  dove help                  Show this help text

Flags for 'send':
  --<N>m         Set session duration in minutes (e.g. --100m, --15m, --5m).
                 Default: unlimited (runs until user stops via Ctrl+C).
  --pin <pin>    Protect file with an optional 4-6 digit passcode.
  --terminal     Display animated QR frames in terminal stdout instead of browser.
  --fps <rate>   Target frame rate per second for stream (default: 14, range: 5-30).

Flags for 'get':
  --output <dir> Target directory to save received file (default: current directory).

Documentation:
  https://github.com/berlin-566/dove
`)
}
