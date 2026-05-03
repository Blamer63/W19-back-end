# Backend 403 Forbidden Debugging Report

## 1. Root Cause
The `403 Forbidden` error on `/api/scanner/analyze` was caused by strict default settings in the Spring Security configuration:
1. **Unpermitted Endpoint:** The endpoint was falling under the global `.anyRequest().authenticated()` rule. Even if the frontend sent a request, it was blocked because the scanner endpoints were not explicitly white-listed for public access.
2. **Incomplete CORS Configuration:** The `CorsConfigurationSource` was using `setAllowedOrigins(List.of("*"))` paired with `setAllowCredentials(false)`. Browsers strictly block preflight `OPTIONS` requests if the frontend attempts to send headers or credentials while the server returns wildcard `*` origins. 

*(Note: CSRF was already disabled correctly via `csrf(AbstractHttpConfigurer::disable)`, so it was not the source of the 403.)*

## 2. Exact Config Changes Made
In `SecurityConfig.java`:
- **SecurityFilterChain:** Added `.requestMatchers("/api/scanner/**").permitAll()` before the `anyRequest().authenticated()` rule.
- **CORS Config:** Updated the configuration to explicitly allow `"http://localhost:5173"` (Vite default) and `"http://localhost:8080"` (Docker frontend).
- **CORS Credentials:** Changed `setAllowCredentials(false)` to `true` to allow frontend requests with custom headers/tokens, which pairs correctly with the explicit origins above.
- **Custom Logging:** Added custom `AuthenticationEntryPoint` (for 401 Unauthorized) and `AccessDeniedHandler` (for 403 Forbidden) Beans that explicitly log the rejection details using SLF4J, including the Request URI, Origin header, and current Authentication status.

## 3. Verification Steps
Once the Spring Boot application restarts with the new configuration:
1. **Frontend Request:** Open the scanner UI and upload an image. The browser will successfully complete the preflight `OPTIONS` request due to the correct CORS headers.
2. **Endpoint Access:** The `POST /api/scanner/analyze` request will bypass the JWT filter because it is explicitly `permitAll()`.
3. **Log Verification:** If a request is ever rejected on another endpoint, you will now see explicit logs like: 
   `WARN  c.e.d.config.SecurityConfig - Request Rejected (403 Forbidden) | Origin: http://localhost:5173 | URI: /api/words | Auth Status: Unknown`
4. **Successful Status:** The frontend will receive a `200 OK` response with the detection data, and the scanner will display results properly.
