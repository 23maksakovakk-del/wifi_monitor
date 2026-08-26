// wifi_monitor.cpp
#include <iostream>
#include <string>
#include <vector>
#include <cstdlib>
#include <cstdio>
#include <memory>
#include <stdexcept>
#include <regex>
#include <chrono>
#include <thread>
#include <fstream>
#include <curl/curl.h>
#include <json/json.h> // using jsoncpp

using namespace std;

struct Result {
    double ping = -1;
    double download = -1;
    double upload = -1;
};

size_t write_callback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t totalSize = size * nmemb;
    ((std::string*)userp)->append((char*)contents, totalSize);
    return totalSize;
}

class Monitor {
private:
    string pingHost;
    int downloadSize;
    int uploadSize;
    int count;
    bool color;
    vector<Result> results;
    CURL* curl;

public:
    Monitor(string host, int down, int up, int cnt, bool col) 
        : pingHost(host), downloadSize(down), uploadSize(up), count(cnt), color(col) {
        curl_global_init(CURL_GLOBAL_DEFAULT);
        curl = curl_easy_init();
    }

    ~Monitor() {
        curl_easy_cleanup(curl);
        curl_global_cleanup();
    }

    double measurePing(const string& host) {
        string cmd = "ping -c 4 " + host + " 2>/dev/null";
        array<char, 128> buffer;
        string output;
        unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd.c_str(), "r"), pclose);
        if (!pipe) return -1;
        while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
            output += buffer.data();
        }
        regex avg_regex(R"(= ([\d.]+)/([\d.]+)/([\d.]+))");
        smatch match;
        if (regex_search(output, match, avg_regex)) {
            if (match.size() > 2) {
                return stod(match[2]);
            }
        }
        return -1;
    }

    double measureDownload() {
        string url = "http://ipv4.download.thinkbroadband.com/5MB.zip";
        if (!curl) return -1;
        string response;
        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
        auto start = chrono::steady_clock::now();
        CURLcode res = curl_easy_perform(curl);
        auto end = chrono::steady_clock::now();
        if (res != CURLE_OK) return -1;
        double elapsed = chrono::duration<double>(end-start).count();
        long totalBytes = response.size();
        return (totalBytes * 8) / (elapsed * 1'000'000);
    }

    double measureUpload() {
        string url = "https://httpbin.org/post";
        if (!curl) return -1;
        int size = uploadSize * 1024 * 1024;
        string data(size, '0');
        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, data.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, data.size());
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
        auto start = chrono::steady_clock::now();
        CURLcode res = curl_easy_perform(curl);
        auto end = chrono::steady_clock::now();
        if (res != CURLE_OK) return -1;
        double elapsed = chrono::duration<double>(end-start).count();
        return (size * 8) / (elapsed * 1'000'000);
    }

    Result runTest() {
        Result res;
        if (!pingHost.empty()) res.ping = measurePing(pingHost);
        if (downloadSize > 0) res.download = measureDownload();
        if (uploadSize > 0) res.upload = measureUpload();
        return res;
    }

    void printResult(const Result& res) {
        if (color) {
            if (res.ping >= 0) cout << "\033[32m🌐 Ping: " << res.ping << " ms\033[0m" << endl;
            if (res.download >= 0) cout << "\033[34m📥 Download: " << res.download << " Mbps\033[0m" << endl;
            if (res.upload >= 0) cout << "\033[36m📤 Upload: " << res.upload << " Mbps\033[0m" << endl;
        } else {
            if (res.ping >= 0) cout << "Ping: " << res.ping << " ms" << endl;
            if (res.download >= 0) cout << "Download: " << res.download << " Mbps" << endl;
            if (res.upload >= 0) cout << "Upload: " << res.upload << " Mbps" << endl;
        }
        cout << endl;
    }

    void run() {
        for (int i = 0; i < count; ++i) {
            cout << "Тест " << i+1 << "/" << count << endl;
            Result res = runTest();
            results.push_back(res);
            printResult(res);
            if (i < count - 1) this_thread::sleep_for(chrono::seconds(1));
        }
    }

    void save(const string& filename) {
        string ext = filename.substr(filename.find_last_of('.') + 1);
        string content;
        if (ext == "json") {
            Json::Value root(Json::arrayValue);
            for (auto& r : results) {
                Json::Value item;
                item["ping"] = r.ping;
                item["download"] = r.download;
                item["upload"] = r.upload;
                root.append(item);
            }
            content = root.toStyledString();
        } else if (ext == "csv") {
            content = "ping,download,upload\n";
            for (auto& r : results) {
                content += to_string(r.ping) + "," + to_string(r.download) + "," + to_string(r.upload) + "\n";
            }
        } else {
            content = "Ping,Download,Upload\n";
            for (auto& r : results) {
                content += to_string(r.ping) + "," + to_string(r.download) + "," + to_string(r.upload) + "\n";
            }
        }
        ofstream file(filename);
        if (file.is_open()) {
            file << content;
            file.close();
            cout << "Результаты сохранены в " << filename << endl;
        }
    }
};

int main(int argc, char* argv[]) {
    string pingHost;
    int downloadSize = 0, uploadSize = 0, count = 1;
    string outputFile;
    bool color = false;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "--ping" && i+1 < argc) pingHost = argv[++i];
        else if (arg == "--download" && i+1 < argc) downloadSize = stoi(argv[++i]);
        else if (arg == "--upload" && i+1 < argc) uploadSize = stoi(argv[++i]);
        else if (arg == "--count" && i+1 < argc) count = stoi(argv[++i]);
        else if (arg == "--output" && i+1 < argc) outputFile = argv[++i];
        else if (arg == "--color") color = true;
    }
    if (pingHost.empty() && downloadSize == 0 && uploadSize == 0) {
        pingHost = "8.8.8.8";
        downloadSize = 5;
        uploadSize = 2;
    }
    color = color || isatty(fileno(stdout));
    Monitor monitor(pingHost, downloadSize, uploadSize, count, color);
    monitor.run();
    if (!outputFile.empty()) monitor.save(outputFile);
    return 0;
}
