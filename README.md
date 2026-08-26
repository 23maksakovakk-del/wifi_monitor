# Монитор Wi-Fi (скорость)

Многоязычная утилита для измерения скорости интернет-соединения (Wi-Fi / Ethernet) с расширенной аналитикой.  
Позволяет измерить скорость загрузки (download), отдачи (upload), задержку (ping) и сохранять историю измерений.

## Особенности
- Измерение скорости загрузки и отдачи в Мбит/с.
- Измерение задержки (ping) до указанного хоста.
- Поддержка множественных тестов (усреднение результатов).
- Выбор сетевого интерфейса (где поддерживается).
- Экспорт результатов в JSON, CSV, текстовый файл.
- Цветное отображение в терминале.
- Настраиваемый размер тестового файла и количество потоков (для многопоточности).
- Сохранение истории измерений в SQLite (опционально).
- Автоматическое определение лучшего сервера (через публичные API).

## Установка и запуск
Для каждого языка требуются соответствующие инструменты.

### Запуск на разных языках

1. **Python**  
   Установка: `pip install requests speedtest-cli colorama`  
   Запуск: `python wifi_monitor.py --ping --download --upload --json result.json`

2. **JavaScript (Node.js)**  
   Установка: `npm install axios commander chalk speedtest-net`  
   Запуск: `node wifi_monitor.js --ping --download --upload`

3. **Go**  
   Установка: модулей не требуется (используем стандартную библиотеку + `go-ping`).  
   Запуск: `go run wifi_monitor.go --ping --download`

4. **Rust**  
   Добавьте `reqwest`, `serde`, `clap`, `ping` в `Cargo.toml`.  
   Запуск: `cargo run -- --ping --download`

5. **Java**  
   Используйте библиотеки OkHttp, Gson, JCommander.  
   Сборка: `javac -cp okhttp.jar:gson.jar:jcommander.jar WiFiMonitor.java`  
   Запуск: `java -cp .;okhttp.jar;gson.jar;jcommander.jar WiFiMonitor --ping`

6. **C# (.NET Core)**  
   Установка: `dotnet add package Newtonsoft.Json` и `System.Net.Ping`.  
   Запуск: `dotnet run -- --ping --download --upload`

7. **C++ (Linux)**  
   Требуется libcurl, nlohmann/json, и библиотека для ping (icmp).  
   Сборка: `g++ -std=c++11 -o wifi_monitor wifi_monitor.cpp -lcurl -ljsoncpp`  
   Запуск: `./wifi_monitor --ping --download`

8. **Kotlin (JVM)**  
   Используйте OkHttp, Gson, JCommander.  
   Сборка: `kotlinc -cp okhttp.jar:gson.jar:jcommander.jar WiFiMonitor.kt`  
   Запуск: `kotlin -cp .;okhttp.jar;gson.jar;jcommander.jar WiFiMonitorKt --ping`

## Использование

Общие аргументы командной строки (везде, где поддерживается):

- `--ping [host]` – измерить задержку до хоста (по умолчанию 8.8.8.8).
- `--download [size]` – измерить скорость загрузки, указав размер в МБ (по умолчанию 5 МБ).
- `--upload [size]` – измерить скорость отдачи, указав размер в МБ (по умолчанию 2 МБ).
- `--count <n>` – количество повторений (по умолчанию 1).
- `--interface <iface>` – указать сетевой интерфейс (не везде поддерживается).
- `--output <file>` – сохранить результат в файл (формат определяется расширением: .json, .csv, .txt).
- `--history` – сохранить результаты в историю (SQLite).
- `--color` – принудительно включить цветной вывод.
- `--threads <n>` – количество параллельных потоков для теста (по умолчанию 1).

Пример (Python):
```bash
python wifi_monitor.py --ping google.com --download 10 --upload 5 --count 3 --output results.json --color
Пример вывода (цветной):

text
🌐 Ping: 12.3 ms
📥 Download: 45.6 Mbps
📤 Upload: 12.3 Mbps
Структура репозитория
text
/
├── README.md
├── wifi_monitor.py
├── wifi_monitor.js
├── wifi_monitor.go
├── wifi_monitor.rs
├── WiFiMonitor.java
├── WiFiMonitor.cs
├── wifi_monitor.cpp
└── WiFiMonitor.kt
Лицензия
MIT
