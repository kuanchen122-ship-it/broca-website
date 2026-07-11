# Broca English Website

布魯卡美語官方網站與內部行政管理系統。專案以 Spring Boot 4、Java 17、MariaDB 與原生 HTML/CSS/JavaScript 建置，包含招生前台、課表匯入、學生與分班、點名、請假、薪資、學習內容及 LINE 整合介面。

## 專案邊界

- GitHub：只存放程式碼、公開圖片、資料庫結構與不含秘密的設定範例。
- Synology NAS：執行 Spring Boot、保存 MariaDB 正式資料及環境變數。
- Namecheap：管理 `broca-english.com` DNS，將流量導向 NAS。
- 不可提交：學生/家長資料、出缺席、薪資、Excel、資料庫檔、cookie、LINE token 或任何正式密碼。

## 本機執行

需要 Java 17。第一次啟動前，先在終端機設定臨時測試帳號：

```powershell
$env:BROCA_BOOTSTRAP_ADMIN_USERNAME = "director"
$env:BROCA_BOOTSTRAP_ADMIN_PASSWORD = "use-a-local-password"
$env:BROCA_BOOTSTRAP_TEACHER_USERNAME = "teacher"
$env:BROCA_BOOTSTRAP_TEACHER_PASSWORD = "use-another-local-password"
.\mvnw.cmd spring-boot:run
```

開啟 `http://localhost:8080/`。本機預設使用 `./data/broca` H2 資料庫；`data/` 已被 Git 排除。

## 測試與建置

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
```

建置後的 JAR 位於 `target/`，該資料夾不會提交到 GitHub。

## NAS 環境變數

以 [`.env.example`](.env.example) 為欄位清單，在 Synology Container Manager 或服務設定中建立真實值。不要重新命名 `.env.example` 後提交，也不要把正式 `.env` 傳到聊天、Email 或 GitHub。

正式啟動需啟用 MariaDB profile：

```text
SPRING_PROFILES_ACTIVE=mariadb
```

首次啟動時，`BROCA_BOOTSTRAP_ADMIN_PASSWORD` 與 `BROCA_BOOTSTRAP_TEACHER_PASSWORD` 會建立或更新帳號，密碼以 Spring Security 編碼後才寫入 MariaDB。後續可保留環境變數作為固定輪替來源，或在完成帳號管理介面後移除 bootstrap 密碼。

## 正式架構

```text
www.broca-english.com
        |
Namecheap DNS
        |
Synology HTTPS / reverse proxy
        |
Spring Boot :8080
        |
MariaDB 10 (NAS only)
```

完整步驟請見 [`docs/NAS-DEPLOYMENT.md`](docs/NAS-DEPLOYMENT.md)。

