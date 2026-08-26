// wifi_monitor.go
package main

import (
	"bytes"
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

type Result struct {
	Ping     *float64 `json:"ping,omitempty"`
	Download *float64 `json:"download,omitempty"`
	Upload   *float64 `json:"upload,omitempty"`
}

type Monitor struct {
	pingHost     string
	downloadSize int
	uploadSize   int
	count        int
	color        bool
	results      []Result
}

func NewMonitor(pingHost string, downloadSize, uploadSize, count int, color bool) *Monitor {
	return &Monitor{
		pingHost:     pingHost,
		downloadSize: downloadSize,
		uploadSize:   uploadSize,
		count:        count,
		color:        color,
	}
}

func (m *Monitor) measurePing(host string) *float64 {
	cmd := exec.Command("ping", "-c", "4", host)
	out, err := cmd.Output()
	if err != nil {
		return nil
	}
	lines := strings.Split(string(out), "\n")
	for _, line := range lines {
		if strings.Contains(line, "avg") || strings.Contains(line, "rtt min/avg/max/mdev") {
			parts := strings.Split(line, "=")
			if len(parts) > 1 {
				fields := strings.Split(parts[1], "/")
				if len(fields) > 1 {
					avg, err := strconv.ParseFloat(fields[1], 64)
					if err == nil {
						return &avg
					}
				}
			}
		}
	}
	return nil
}

func (m *Monitor) measureDownload() *float64 {
	url := "http://ipv4.download.thinkbroadband.com/5MB.zip"
	start := time.Now()
	resp, err := http.Get(url)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	var total int64
	buf := make([]byte, 8192)
	for {
		n, err := resp.Body.Read(buf)
		if err != nil && err != io.EOF {
			return nil
		}
		total += int64(n)
		if err == io.EOF {
			break
		}
	}
	elapsed := time.Since(start).Seconds()
	speed := float64(total*8) / (elapsed * 1_000_000)
	return &speed
}

func (m *Monitor) measureUpload() *float64 {
	url := "https://httpbin.org/post"
	size := m.uploadSize * 1024 * 1024
	data := bytes.Repeat([]byte("0"), size)
	start := time.Now()
	_, err := http.Post(url, "application/octet-stream", bytes.NewReader(data))
	if err != nil {
		return nil
	}
	elapsed := time.Since(start).Seconds()
	speed := float64(size*8) / (elapsed * 1_000_000)
	return &speed
}

func (m *Monitor) runTest() Result {
	res := Result{}
	if m.pingHost != "" {
		res.Ping = m.measurePing(m.pingHost)
	}
	if m.downloadSize > 0 {
		res.Download = m.measureDownload()
	}
	if m.uploadSize > 0 {
		res.Upload = m.measureUpload()
	}
	return res
}

func (m *Monitor) printResult(res Result) {
	if m.color {
		if res.Ping != nil {
			fmt.Printf("\033[32m🌐 Ping: %.2f ms\033[0m\n", *res.Ping)
		}
		if res.Download != nil {
			fmt.Printf("\033[34m📥 Download: %.2f Mbps\033[0m\n", *res.Download)
		}
		if res.Upload != nil {
			fmt.Printf("\033[36m📤 Upload: %.2f Mbps\033[0m\n", *res.Upload)
		}
	} else {
		if res.Ping != nil {
			fmt.Printf("Ping: %.2f ms\n", *res.Ping)
		}
		if res.Download != nil {
			fmt.Printf("Download: %.2f Mbps\n", *res.Download)
		}
		if res.Upload != nil {
			fmt.Printf("Upload: %.2f Mbps\n", *res.Upload)
		}
	}
	fmt.Println()
}

func (m *Monitor) Run() {
	for i := 0; i < m.count; i++ {
		fmt.Printf("Тест %d/%d\n", i+1, m.count)
		res := m.runTest()
		m.results = append(m.results, res)
		m.printResult(res)
		if i < m.count-1 {
			time.Sleep(1 * time.Second)
		}
	}
}

func (m *Monitor) Save(filename string) error {
	ext := strings.ToLower(filename[strings.LastIndex(filename, ".")+1:])
	var data []byte
	switch ext {
	case "json":
		d, err := json.MarshalIndent(m.results, "", "  ")
		if err != nil {
			return err
		}
		data = d
	case "csv":
		var buf bytes.Buffer
		w := csv.NewWriter(&buf)
		w.Write([]string{"ping", "download", "upload"})
		for _, r := range m.results {
			row := []string{
				fmt.Sprintf("%v", r.Ping),
				fmt.Sprintf("%v", r.Download),
				fmt.Sprintf("%v", r.Upload),
			}
			w.Write(row)
		}
		w.Flush()
		data = buf.Bytes()
	default:
		// text
		var s string
		for _, r := range m.results {
			s += fmt.Sprintf("%v\n", r)
		}
		data = []byte(s)
	}
	return os.WriteFile(filename, data, 0644)
}

func main() {
	var (
		pingHost     string
		downloadSize int
		uploadSize   int
		count        int
		output       string
		color        bool
	)
	flag.StringVar(&pingHost, "ping", "", "Хост для пинга")
	flag.IntVar(&downloadSize, "download", 0, "Размер для загрузки в МБ")
	flag.IntVar(&uploadSize, "upload", 0, "Размер для отдачи в МБ")
	flag.IntVar(&count, "count", 1, "Количество тестов")
	flag.StringVar(&output, "output", "", "Файл для сохранения")
	flag.BoolVar(&color, "color", false, "Принудительно включить цвет")
	flag.Parse()

	if pingHost == "" && downloadSize == 0 && uploadSize == 0 {
		pingHost = "8.8.8.8"
		downloadSize = 5
		uploadSize = 2
	}
	monitor := NewMonitor(pingHost, downloadSize, uploadSize, count, color || isTerminal())
	monitor.Run()
	if output != "" {
		if err := monitor.Save(output); err != nil {
			fmt.Fprintf(os.Stderr, "Ошибка сохранения: %v\n", err)
		} else {
			fmt.Printf("Результаты сохранены в %s\n", output)
		}
	}
}

func isTerminal() bool {
	fileInfo, _ := os.Stdout.Stat()
	return (fileInfo.Mode() & os.ModeCharDevice) != 0
}
