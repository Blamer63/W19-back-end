---
description: Expose local backend via ngrok
---

1.  **Ensure Backend is Running**
    Make sure your Spring Boot app is running on port 8081.
    ```powershell
    curl http://localhost:8081/actuator/health
    # or just check if the terminal is still active
    ```

2.  **Install/Run Ngrok**
    If you don't have ngrok installed, download it from [ngrok.com](https://ngrok.com/download) or use Chocolatey:
    ```powershell
    choco install ngrok
    ```

3.  **Start Tunnel**
    Run the following command to expose port 8081:
    ```powershell
    ngrok http 8081
    ```

4.  **Copy Public URL**
    Copy the `https://xxxx-xx-xx-xx-xx.ngrok-free.app` URL from the terminal output.

5.  **Update Frontend**
    Give this URL to the frontend team to use as their `API_BASE_URL`.
