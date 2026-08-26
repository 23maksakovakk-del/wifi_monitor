// WiFiMonitor.cs
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using Newtonsoft.Json;

namespace WiFiMonitor
{
    class Result
    {
        public double? Ping { get; set; }
        public double? Download { get; set; }
        public double? Upload { get; set; }
    }

    class Monitor
    {
        private string pingHost;
        private int downloadSize;
        private int uploadSize;
        private int count;
        private bool color;
        private List<Result> results = new List<Result>();
        private HttpClient client = new HttpClient();

        public Monitor(string pingHost, int downloadSize, int uploadSize, int count, bool color)
        {
            this.pingHost = pingHost;
            this.downloadSize = downloadSize;
            this.uploadSize = uploadSize;
            this.count = count;
            this.color = color || Console.IsOutputRedirected == false;
        }

        private double? MeasurePing(string host)
        {
            try
            {
                var process = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = "ping",
                        Arguments = $"-c 4 {host}",
                        RedirectStandardOutput = true,
                        UseShellExecute = false,
                        CreateNoWindow = true
                    }
                };
                process.Start();
                string output = process.StandardOutput.ReadToEnd();
                process.WaitForExit();
                foreach (var line in output.Split('\n'))
                {
                    if (line.Contains("avg") || line.Contains("rtt min/avg/max/mdev"))
                    {
                        var parts = line.Split('=');
                        if (parts.Length > 1)
                        {
                            var nums = parts[1].Split('/');
                            if (nums.Length > 1 && double.TryParse(nums[1], out double avg))
                                return avg;
                        }
                    }
                }
            }
            catch { }
            return null;
        }

        private double? MeasureDownload()
        {
            string url = "http://ipv4.download.thinkbroadband.com/5MB.zip";
            try
            {
                var start = DateTime.Now;
                var response = client.GetAsync(url).Result;
                long total = 0;
                var stream = response.Content.ReadAsStreamAsync().Result;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                    total += read;
                double elapsed = (DateTime.Now - start).TotalSeconds;
                return (total * 8) / (elapsed * 1_000_000);
            }
            catch { return null; }
        }

        private double? MeasureUpload()
        {
            string url = "https://httpbin.org/post";
            int size = uploadSize * 1024 * 1024;
            byte[] data = new byte[size];
            for (int i = 0; i < data.Length; i++) data[i] = 48; // '0'
            try
            {
                var start = DateTime.Now;
                var content = new ByteArrayContent(data);
                content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
                var response = client.PostAsync(url, content).Result;
                double elapsed = (DateTime.Now - start).TotalSeconds;
                return (size * 8) / (elapsed * 1_000_000);
            }
            catch { return null; }
        }

        private Result RunTest()
        {
            var res = new Result();
            if (!string.IsNullOrEmpty(pingHost)) res.Ping = MeasurePing(pingHost);
            if (downloadSize > 0) res.Download = MeasureDownload();
            if (uploadSize > 0) res.Upload = MeasureUpload();
            return res;
        }

        private void PrintResult(Result res)
        {
            if (color)
            {
                if (res.Ping.HasValue) Console.ForegroundColor = ConsoleColor.Green;
                else Console.ResetColor();
                if (res.Ping.HasValue) Console.WriteLine($"🌐 Ping: {res.Ping.Value:F2} ms");
                if (res.Download.HasValue) { Console.ForegroundColor = ConsoleColor.Blue; Console.WriteLine($"📥 Download: {res.Download.Value:F2} Mbps"); }
                if (res.Upload.HasValue) { Console.ForegroundColor = ConsoleColor.Cyan; Console.WriteLine($"📤 Upload: {res.Upload.Value:F2} Mbps"); }
                Console.ResetColor();
            }
            else
            {
                if (res.Ping.HasValue) Console.WriteLine($"Ping: {res.Ping.Value:F2} ms");
                if (res.Download.HasValue) Console.WriteLine($"Download: {res.Download.Value:F2} Mbps");
                if (res.Upload.HasValue) Console.WriteLine($"Upload: {res.Upload.Value:F2} Mbps");
            }
            Console.WriteLine();
        }

        public async Task RunAsync()
        {
            for (int i = 0; i < count; i++)
            {
                Console.WriteLine($"Тест {i+1}/{count}");
                var res = RunTest();
                results.Add(res);
                PrintResult(res);
                if (i < count - 1) await Task.Delay(1000);
            }
        }

        public void Save(string filename)
        {
            string ext = Path.GetExtension(filename).ToLower().TrimStart('.');
            string content;
            if (ext == "json")
                content = JsonConvert.SerializeObject(results, Formatting.Indented);
            else if (ext == "csv")
            {
                var sb = new StringBuilder("ping,download,upload\n");
                foreach (var r in results)
                    sb.AppendLine($"{r.Ping},{r.Download},{r.Upload}");
                content = sb.ToString();
            }
            else
                content = string.Join("\n", results);
            File.WriteAllText(filename, content);
            Console.WriteLine($"Результаты сохранены в {filename}");
        }

        public static async Task Main(string[] args)
        {
            string pingHost = null;
            int downloadSize = 0, uploadSize = 0, count = 1;
            string output = null;
            bool color = false;

            for (int i = 0; i < args.Length; i++)
            {
                switch (args[i])
                {
                    case "--ping": pingHost = args[++i]; break;
                    case "--download": downloadSize = int.Parse(args[++i]); break;
                    case "--upload": uploadSize = int.Parse(args[++i]); break;
                    case "--count": count = int.Parse(args[++i]); break;
                    case "--output": output = args[++i]; break;
                    case "--color": color = true; break;
                }
            }
            if (pingHost == null && downloadSize == 0 && uploadSize == 0)
            {
                pingHost = "8.8.8.8";
                downloadSize = 5;
                uploadSize = 2;
            }
            var monitor = new Monitor(pingHost, downloadSize, uploadSize, count, color);
            await monitor.RunAsync();
            if (output != null) monitor.Save(output);
        }
    }
}
