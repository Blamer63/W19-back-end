# W19 Back-End Service

## Configuration

This application uses a `.env` file to manage environment variables.

### Setup

1.  **Create a `.env` file** in the root directory of the project (if it doesn't already exist).
2.  Add the following variables to the file:

    ```properties
    GOOGLE_PLACES_KEY=your_actual_api_key
    JWT_SECRET=your_secure_random_seceret
    ```

    > **Note:** A `.env` file with your keys has been created for you locally.

3.  **Run the application**:
    ```bash
    ./mvnw spring-boot:run
    ```
    The application will automatically load the variables from the `.env` file.

- `GOOGLE_PLACES_KEY`: Your Google Maps Places API Key.
- `JWT_SECRET`: A secure random string for signing JWT tokens.

## Documentation

- **[API Reference](./docs/api_reference.md)**: Main endpoint documentation.
- **[Frontend Integration](./docs/frontend_integration.md)**: Guide for frontend developers.
- **[Chat Integration Guide](./docs/chat_integration_guide.md)**: Specific details for implementing the chat feature.
- **[Technical Notes](./docs/technical_notes.md)**: Architecture and design decisions.
