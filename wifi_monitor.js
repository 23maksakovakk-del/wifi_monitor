// wifi_monitor.js
const { program } = require('commander');
const axios = require('axios');
const chalk = require('chalk');
const speedtest = require('speedtest-net');
const { exec } = require('child_process');
const fs = require('fs');
const { promisify } = require('util');
const execPromise = promisify(exec);

class WiFiMonitor {
    constructor(options) {
        this.pingHost = options.ping || null;
        this.downloadSize = options.download || 0;
        this.uploadSize = options.upload || 0;
        this.count = options.count || 1;
        this.color = options.color || process.stdout.isTTY;
        this.results = [];
    }

    async measurePing(host = '8.8.8.8', count = 4) {
        try {
            const { stdout } = await execPromise(`ping -c ${count} ${host}`);
            const lines = stdout.split('\n');
            for (let line of lines) {
                if (line.includes('avg') || line.includes('rtt min/avg/max/mdev')) {
                    const match = line.match(/= ([\d.]+)\/([\d.]+)\/([\d.]+)/);
                    if (match) {
                        return parseFloat(match[2]); // avg
                    }
                }
            }
            return null;
        } catch (e) {
            return null;
        }
    }

    async measureDownload() {
        const url = 'http://ipv4.download.thinkbroadband.com/5MB.zip';
        try {
            const start = Date.now();
            const response = await axios.get(url, { responseType: 'stream' });
            let total = 0;
            return new Promise((resolve) => {
                response.data.on('data', (chunk) => { total += chunk.length; });
                response.data.on('end', () => {
                    const elapsed = (Date.now() - start) / 1000;
                    const speed = (total * 8) / (elapsed * 1_000_000);
                    resolve(speed);
                });
                response.data.on('error', () => resolve(null));
            });
        } catch (e) {
            return null;
        }
    }

    async measureUpload() {
        const url = 'https://httpbin.org/post';
        const size = this.uploadSize * 1024 * 1024;
        const data = Buffer.alloc(size, '0');
        try {
            const start = Date.now();
            await axios.post(url, data);
            const elapsed = (Date.now() - start) / 1000;
            const speed = (size * 8) / (elapsed * 1_000_000);
            return speed;
        } catch (e) {
            return null;
        }
    }

    async runTest() {
        const result = {};
        if (this.pingHost) {
            result.ping = await this.measurePing(this.pingHost);
        }
        if (this.downloadSize > 0) {
            result.download = await this.measureDownload();
        }
        if (this.uploadSize > 0) {
            result.upload = await this.measureUpload();
        }
        return result;
    }

    async run() {
        for (let i = 0; i < this.count; i++) {
            console.log(`Тест ${i+1}/${this.count}`);
            const res = await this.runTest();
            this.results.push(res);
            this.printResult(res);
            if (i < this.count - 1) await this.sleep(1000);
        }
    }

    printResult(res) {
        if (this.color) {
            if (res.ping) console.log(chalk.green(`🌐 Ping: ${res.ping.toFixed(2)} ms`));
            if (res.download) console.log(chalk.blue(`📥 Download: ${res.download.toFixed(2)} Mbps`));
            if (res.upload) console.log(chalk.cyan(`📤 Upload: ${res.upload.toFixed(2)} Mbps`));
        } else {
            if (res.ping) console.log(`Ping: ${res.ping.toFixed(2)} ms`);
            if (res.download) console.log(`Download: ${res.download.toFixed(2)} Mbps`);
            if (res.upload) console.log(`Upload: ${res.upload.toFixed(2)} Mbps`);
        }
        console.log();
    }

    sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

    saveResults(filename) {
        const ext = filename.split('.').pop().toLowerCase();
        let data;
        if (ext === 'json') {
            data = JSON.stringify(this.results, null, 2);
        } else if (ext === 'csv') {
            const header = 'ping,download,upload\n';
            const rows = this.results.map(r => `${r.ping||''},${r.download||''},${r.upload||''}`).join('\n');
            data = header + rows;
        } else {
            data = this.results.map(r => JSON.stringify(r)).join('\n');
        }
        fs.writeFileSync(filename, data);
        console.log(`Результаты сохранены в ${filename}`);
    }
}

program
    .option('-p, --ping [host]', 'Измерить пинг (по умолчанию 8.8.8.8)')
    .option('-d, --download [size]', 'Размер для загрузки в МБ', parseInt)
    .option('-u, --upload [size]', 'Размер для отдачи в МБ', parseInt)
    .option('-c, --count <number>', 'Количество тестов', parseInt, 1)
    .option('-o, --output <file>', 'Файл для сохранения')
    .option('--color', 'Принудительно включить цвет')
    .parse(process.argv);

const opts = program.opts();
if (!opts.ping && !opts.download && !opts.upload) {
    opts.ping = '8.8.8.8';
    opts.download = 5;
    opts.upload = 2;
}
const monitor = new WiFiMonitor(opts);
monitor.run().then(() => {
    if (opts.output) monitor.saveResults(opts.output);
});
