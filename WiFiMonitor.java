// WiFiMonitor.java
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class WiFiMonitor {
    @Parameter(names = "--ping", description = "Хост для пинга")
    String pingHost;

    @Parameter(names = "--download", description = "Размер загрузки в МБ")
    Integer downloadSize;

    @Parameter(names = "--upload", description = "Размер отдачи в МБ")
    Integer uploadSize;

    @Parameter(names = "--count", description = "Количество тестов")
    int count = 1;

    @Parameter(names = "--output", description = "Файл для сохранения")
    String outputFile;

    @Parameter(names = "--color", description = "Принудительный цвет")
    boolean color;

    private List<Result> results = new ArrayList<>();
    private OkHttpClient client = new OkHttpClient();

    static class Result {
        Double ping;
        Double download;
        Double upload;
    }

    private Double measurePing(String host) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"ping", "-c", "4", host});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("avg") || line.contains("rtt min/avg/max/mdev")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        String[] nums = parts[1].split("/");
                        if (nums.length > 1) {
                            return Double.parseDouble(nums[1]);
                        }
                    }
                }
            }
        } catch (Exception e) { }
        return null;
    }

    private Double measureDownload() {
        String url = "http://ipv4.download.thinkbroadband.com/5MB.zip";
        Request request = new Request.Builder().url(url).build();
        try {
            long start = System.nanoTime();
            Response response = client.newCall(request).execute();
            long total = 0;
            byte[] buffer = new byte[8192];
            InputStream is = response.body().byteStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
            }
            double elapsed = (System.nanoTime() - start) / 1e9;
            return (total * 8) / (elapsed * 1_000_000);
        } catch (Exception e) {
            return null;
        }
    }

    private Double measureUpload() {
        String url = "https://httpbin.org/post";
        int size = uploadSize * 1024 * 1024;
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) '0');
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), data);
        Request request = new Request.Builder().url(url).post(body).build();
        try {
            long start = System.nanoTime();
            client.newCall(request).execute();
            double elapsed = (System.nanoTime() - start) / 1e9;
            return (size * 8) / (elapsed * 1_000_000);
        } catch (Exception e) {
            return null;
        }
    }

    private Result runTest() {
        Result res = new Result();
        if (pingHost != null) res.ping = measurePing(pingHost);
        if (downloadSize != null && downloadSize > 0) res.download = measureDownload();
        if (uploadSize != null && uploadSize > 0) res.upload = measureUpload();
        return res;
    }

    private void printResult(Result res) {
        if (color) {
            if (res.ping != null) System.out.println("\u001B[32m🌐 Ping: " + String.format("%.2f", res.ping) + " ms\u001B[0m");
            if (res.download != null) System.out.println("\u001B[34m📥 Download: " + String.format("%.2f", res.download) + " Mbps\u001B[0m");
            if (res.upload != null) System.out.println("\u001B[36m📤 Upload: " + String.format("%.2f", res.upload) + " Mbps\u001B[0m");
        } else {
            if (res.ping != null) System.out.println("Ping: " + String.format("%.2f", res.ping) + " ms");
            if (res.download != null) System.out.println("Download: " + String.format("%.2f", res.download) + " Mbps");
            if (res.upload != null) System.out.println("Upload: " + String.format("%.2f", res.upload) + " Mbps");
        }
        System.out.println();
    }

    public void run() throws Exception {
        for (int i = 0; i < count; i++) {
            System.out.println("Тест " + (i+1) + "/" + count);
            Result res = runTest();
            results.add(res);
            printResult(res);
            if (i < count - 1) Thread.sleep(1000);
        }
    }

    public void save() throws IOException {
        if (outputFile == null) return;
        String ext = outputFile.substring(outputFile.lastIndexOf('.') + 1).toLowerCase();
        String content;
        if (ext.equals("json")) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            content = gson.toJson(results);
        } else if (ext.equals("csv")) {
            StringBuilder sb = new StringBuilder("ping,download,upload\n");
            for (Result r : results) {
                sb.append(r.ping != null ? r.ping : "").append(",");
                sb.append(r.download != null ? r.download : "").append(",");
                sb.append(r.upload != null ? r.upload : "").append("\n");
            }
            content = sb.toString();
        } else {
            content = results.toString();
        }
        Files.write(Paths.get(outputFile), content.getBytes());
        System.out.println("Результаты сохранены в " + outputFile);
    }

    public static void main(String[] args) throws Exception {
        WiFiMonitor monitor = new WiFiMonitor();
        JCommander.newBuilder().addObject(monitor).build().parse(args);
        // Если ничего не задано, то все по умолчанию
        if (monitor.pingHost == null && monitor.downloadSize == null && monitor.uploadSize == null) {
            monitor.pingHost = "8.8.8.8";
            monitor.downloadSize = 5;
            monitor.uploadSize = 2;
        }
        monitor.color = monitor.color || System.console() != null;
        monitor.run();
        if (monitor.outputFile != null) monitor.save();
    }
}
