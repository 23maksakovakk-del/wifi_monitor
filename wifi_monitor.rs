// wifi_monitor.rs
use clap::{Arg, App};
use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use std::process::Command;
use std::fs::File;
use std::io::Write;
use std::time::{Instant, Duration};
use std::thread::sleep;
use colored::*;

#[derive(Serialize, Deserialize, Debug, Clone)]
struct Result {
    ping: Option<f64>,
    download: Option<f64>,
    upload: Option<f64>,
}

struct Monitor {
    ping_host: Option<String>,
    download_size: usize,
    upload_size: usize,
    count: usize,
    color: bool,
    results: Vec<Result>,
}

impl Monitor {
    fn new(ping_host: Option<String>, download_size: usize, upload_size: usize, count: usize, color: bool) -> Self {
        Monitor { ping_host, download_size, upload_size, count, color, results: Vec::new() }
    }

    fn measure_ping(&self, host: &str) -> Option<f64> {
        let output = Command::new("ping").args(&["-c", "4", host]).output().ok()?;
        let s = String::from_utf8_lossy(&output.stdout);
        for line in s.lines() {
            if line.contains("avg") || line.contains("rtt min/avg/max/mdev") {
                if let Some(parts) = line.split('=').nth(1) {
                    let fields: Vec<&str> = parts.split('/').collect();
                    if fields.len() > 1 {
                        if let Ok(avg) = fields[1].parse::<f64>() {
                            return Some(avg);
                        }
                    }
                }
            }
        }
        None
    }

    fn measure_download(&self) -> Option<f64> {
        let url = "http://ipv4.download.thinkbroadband.com/5MB.zip";
        let client = Client::new();
        let start = Instant::now();
        let mut total = 0u64;
        if let Ok(mut resp) = client.get(url).send() {
            let mut buf = [0u8; 8192];
            loop {
                match resp.read(&mut buf) {
                    Ok(n) if n > 0 => { total += n as u64; }
                    Ok(_) => break,
                    Err(_) => return None,
                }
            }
            let elapsed = start.elapsed().as_secs_f64();
            let speed = (total as f64 * 8.0) / (elapsed * 1_000_000.0);
            return Some(speed);
        }
        None
    }

    fn measure_upload(&self) -> Option<f64> {
        let url = "https://httpbin.org/post";
        let size = self.upload_size * 1024 * 1024;
        let data = vec![0u8; size];
        let client = Client::new();
        let start = Instant::now();
        if client.post(url).body(data).send().is_ok() {
            let elapsed = start.elapsed().as_secs_f64();
            let speed = (size as f64 * 8.0) / (elapsed * 1_000_000.0);
            return Some(speed);
        }
        None
    }

    fn run_test(&self) -> Result {
        let mut res = Result { ping: None, download: None, upload: None };
        if let Some(ref host) = self.ping_host {
            res.ping = self.measure_ping(host);
        }
        if self.download_size > 0 {
            res.download = self.measure_download();
        }
        if self.upload_size > 0 {
            res.upload = self.measure_upload();
        }
        res
    }

    fn print_result(&self, res: &Result) {
        if self.color {
            if let Some(p) = res.ping {
                println!("{}", format!("🌐 Ping: {:.2} ms", p).green());
            }
            if let Some(d) = res.download {
                println!("{}", format!("📥 Download: {:.2} Mbps", d).blue());
            }
            if let Some(u) = res.upload {
                println!("{}", format!("📤 Upload: {:.2} Mbps", u).cyan());
            }
        } else {
            if let Some(p) = res.ping { println!("Ping: {:.2} ms", p); }
            if let Some(d) = res.download { println!("Download: {:.2} Mbps", d); }
            if let Some(u) = res.upload { println!("Upload: {:.2} Mbps", u); }
        }
        println!();
    }

    fn run(&mut self) {
        for i in 0..self.count {
            println!("Тест {}/{}", i+1, self.count);
            let res = self.run_test();
            self.results.push(res.clone());
            self.print_result(&res);
            if i < self.count - 1 {
                sleep(Duration::from_secs(1));
            }
        }
    }

    fn save(&self, filename: &str) -> std::io::Result<()> {
        let ext = filename.split('.').last().unwrap_or("txt").to_lowercase();
        let data: Vec<u8> = match ext.as_str() {
            "json" => serde_json::to_string_pretty(&self.results).unwrap().into_bytes(),
            "csv" => {
                let mut s = "ping,download,upload\n".to_string();
                for r in &self.results {
                    s.push_str(&format!("{},{},{}\n",
                        r.ping.map_or("".to_string(), |v| v.to_string()),
                        r.download.map_or("".to_string(), |v| v.to_string()),
                        r.upload.map_or("".to_string(), |v| v.to_string())
                    ));
                }
                s.into_bytes()
            }
            _ => format!("{:?}", self.results).into_bytes()
        };
        let mut file = File::create(filename)?;
        file.write_all(&data)?;
        Ok(())
    }
}

fn main() {
    let matches = App::new("WiFi Monitor")
        .arg(Arg::with_name("ping").long("ping").takes_value(true).help("Хост для пинга"))
        .arg(Arg::with_name("download").long("download").takes_value(true).help("Размер загрузки в МБ"))
        .arg(Arg::with_name("upload").long("upload").takes_value(true).help("Размер отдачи в МБ"))
        .arg(Arg::with_name("count").long("count").takes_value(true).default_value("1").help("Количество тестов"))
        .arg(Arg::with_name("output").long("output").takes_value(true).help("Файл для сохранения"))
        .arg(Arg::with_name("color").long("color").help("Принудительный цвет"))
        .get_matches();

    let ping_host = matches.value_of("ping").map(|s| s.to_string());
    let download_size = matches.value_of("download").and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
    let upload_size = matches.value_of("upload").and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
    let count = matches.value_of("count").and_then(|s| s.parse::<usize>().ok()).unwrap_or(1);
    let output = matches.value_of("output").map(|s| s.to_string());
    let color = matches.is_present("color") || atty::is(atty::Stream::Stdout);

    if ping_host.is_none() && download_size == 0 && upload_size == 0 {
        // default all
        let mut m = Monitor::new(Some("8.8.8.8".to_string()), 5, 2, count, color);
        m.run();
        if let Some(f) = output {
            if let Err(e) = m.save(&f) {
                eprintln!("Ошибка сохранения: {}", e);
            } else {
                println!("Результаты сохранены в {}", f);
            }
        }
    } else {
        let mut m = Monitor::new(ping_host, download_size, upload_size, count, color);
        m.run();
        if let Some(f) = output {
            if let Err(e) = m.save(&f) {
                eprintln!("Ошибка сохранения: {}", e);
            } else {
                println!("Результаты сохранены в {}", f);
            }
        }
    }
}
