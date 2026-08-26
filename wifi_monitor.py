
```python
# wifi_monitor.py
import argparse
import json
import csv
import time
import sys
import subprocess
import requests
import threading
from concurrent.futures import ThreadPoolExecutor
from colorama import init, Fore, Style
import speedtest  # speedtest-cli

init(autoreset=True)

class WiFiMonitor:
    def __init__(self, ping_host=None, download_size=5, upload_size=2, count=1, threads=1, color=False):
        self.ping_host = ping_host
        self.download_size = download_size  # MB
        self.upload_size = upload_size      # MB
        self.count = count
        self.threads = threads
        self.color = color or sys.stdout.isatty()
        self.results = []

    def measure_ping(self, host="8.8.8.8", count=4):
        """Измеряет средний пинг через системную утилиту ping"""
        try:
            output = subprocess.check_output(["ping", "-c", str(count), host], universal_newlines=True)
            # Парсим среднее время
            for line in output.splitlines():
                if "avg" in line or "rtt min/avg/max/mdev" in line:
                    parts = line.split("=")
                    if len(parts) > 1:
                        avg = parts[1].split("/")[1]
                        return float(avg)
            return None
        except:
            return None

    def measure_download_speed(self):
        """Измеряет скорость загрузки скачиванием тестового файла"""
        url = "http://ipv4.download.thinkbroadband.com/5MB.zip"  # 5 MB
        # Для больших размеров можно использовать другой источник
        if self.download_size > 5:
            # Используем другой сервис, например, speedtest
            st = speedtest.Speedtest()
            return st.download() / 1_000_000  # в Мбит/с
        else:
            try:
                start = time.time()
                response = requests.get(url, stream=True)
                total_size = 0
                chunk_size = 8192
                for chunk in response.iter_content(chunk_size=chunk_size):
                    total_size += len(chunk)
                elapsed = time.time() - start
                speed_mbps = (total_size * 8) / (elapsed * 1_000_000)
                return speed_mbps
            except:
                return None

    def measure_upload_speed(self):
        """Измеряет скорость отдачи через отправку данных на httpbin"""
        url = "https://httpbin.org/post"
        data_size = self.upload_size * 1024 * 1024  # байт
        data = b"0" * data_size  # создаём тестовые данные
        try:
            start = time.time()
            response = requests.post(url, data=data)
            elapsed = time.time() - start
            speed_mbps = (data_size * 8) / (elapsed * 1_000_000)
            return speed_mbps
        except:
            return None

    def run_test(self):
        results = {}
        if self.ping_host:
            ping_result = self.measure_ping(self.ping_host)
            results["ping"] = ping_result
        if self.download_size:
            down = self.measure_download_speed()
            results["download"] = down
        if self.upload_size:
            up = self.measure_upload_speed()
            results["upload"] = up
        return results

    def run(self):
        for i in range(self.count):
            print(f"Тест {i+1}/{self.count}")
            res = self.run_test()
            self.results.append(res)
            self.print_result(res)
            if i < self.count - 1:
                time.sleep(1)

    def print_result(self, res):
        if self.color:
            if "ping" in res and res["ping"] is not None:
                print(Fore.GREEN + f"🌐 Ping: {res['ping']:.2f} ms")
            if "download" in res and res["download"] is not None:
                print(Fore.BLUE + f"📥 Download: {res['download']:.2f} Mbps")
            if "upload" in res and res["upload"] is not None:
                print(Fore.CYAN + f"📤 Upload: {res['upload']:.2f} Mbps")
        else:
            if "ping" in res and res["ping"] is not None:
                print(f"Ping: {res['ping']:.2f} ms")
            if "download" in res and res["download"] is not None:
                print(f"Download: {res['download']:.2f} Mbps")
            if "upload" in res and res["upload"] is not None:
                print(f"Upload: {res['upload']:.2f} Mbps")
        print()

    def save_results(self, filename):
        ext = filename.split('.')[-1].lower()
        if ext == 'json':
            with open(filename, 'w') as f:
                json.dump(self.results, f, indent=2)
        elif ext == 'csv':
            with open(filename, 'w', newline='') as f:
                writer = csv.DictWriter(f, fieldnames=['ping', 'download', 'upload'])
                writer.writeheader()
                writer.writerows(self.results)
        else:
            with open(filename, 'w') as f:
                for res in self.results:
                    f.write(str(res) + '\n')
        print(f"Результаты сохранены в {filename}")

def main():
    parser = argparse.ArgumentParser(description="Монитор Wi-Fi скорости")
    parser.add_argument("--ping", nargs='?', const="8.8.8.8", help="Измерить пинг до хоста (по умолчанию 8.8.8.8)")
    parser.add_argument("--download", nargs='?', const=5, type=int, help="Размер файла для загрузки в МБ")
    parser.add_argument("--upload", nargs='?', const=2, type=int, help="Размер данных для отдачи в МБ")
    parser.add_argument("--count", type=int, default=1, help="Количество тестов")
    parser.add_argument("--output", help="Файл для сохранения (.json, .csv, .txt)")
    parser.add_argument("--color", action="store_true", help="Принудительно включить цвет")
    args = parser.parse_args()

    # Если не указаны опции, выполняем все
    if not args.ping and not args.download and not args.upload:
        args.ping = "8.8.8.8"
        args.download = 5
        args.upload = 2

    monitor = WiFiMonitor(
        ping_host=args.ping,
        download_size=args.download,
        upload_size=args.upload,
        count=args.count,
        color=args.color
    )
    monitor.run()
    if args.output:
        monitor.save_results(args.output)

if __name__ == "__main__":
    main()
