# Hisaabi Finance Manager

Desktop finance-management application built with Java, JavaFX, FXML, Maven and Firebase.

## Phase 4 - Firebase Authentication

The desktop application uses Firebase Authentication's HTTPS REST API for email/password authentication. Firebase's official REST API provides endpoints for sign-up, sign-in, password reset and ID-token refresh.

### Firebase project setup

1. Create a Firebase project in the Firebase Console.
2. Open **Authentication** → **Sign-in method**.
3. Enable **Email/Password**.
4. Open **Project settings** → **Your apps** and create/select a Web app.
5. Copy the project's **Web API Key**.

Do not add the key directly to Java source code or commit private Firebase service-account credentials.

### Configure Windows

For the current PowerShell session:

```powershell
$env:FIREBASE_WEB_API_KEY="YOUR_FIREBASE_WEB_API_KEY"
```

Then run:

```powershell
mvn clean javafx:run
```

For a persistent Windows environment variable, use `setx` and then open a new terminal:

```powershell
setx FIREBASE_WEB_API_KEY "YOUR_FIREBASE_WEB_API_KEY"
```

### Authentication flow

```text
JavaFX Login/Register UI
        ↓
FirebaseAuthService
        ↓
Firebase Authentication REST API
        ↓
Firebase ID token + refresh token
        ↓
In-memory AuthSession
        ↓
Authenticated application shell
```

ID tokens are short-lived. The service also contains refresh-token support so later Firestore requests can use a current ID token.

The application currently keeps the authentication session in memory only; persistent desktop credential storage is intentionally deferred until there is a clear security design for it.
