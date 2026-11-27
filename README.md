# NTU Chat Application — Java

## Network-Based Chat Server \& Client



## Project Structure



chatapp/
├── src/
│   ├── shared/
│   │   ── Message.java          # Serialisable message model (type-tagged protocol)
│   ├── server/
│   │   ── ChatServer.java       # Entry point; accepts connections, manages thread pool
│   │   ── ClientHandler.java    # One thread per client; auth, routing, broadcast
│   │   ── AuthManager.java      # User registration \& login with salted SHA-256
│   │   ── RateLimiter.java      # Sliding-window rate limiter (brute-force / flood)
│   │   ── SecurityLogger.java   # Logs security events to console + security.log
│   ── client/
│       ── ChatClient.java       # Client with separate reader \& writer threads
└── README.md

### Manual



# Compile

mkdir bin
find src -name "\*.java" | xargs javac -d bin -sourcepath src

# Start server

java -cp bin server.ChatServer

# Start client (new terminal each time)

java -cp bin client.ChatClient





## Demo Users (pre-registered)

|Username|Password|
|-|-|
|admin|admin123|
|alice|alice123|
|bob|bob123|



## Client Commands

|Command|Action|
|-|-|
|`/login <user> <pass>`|Log in with existing account|
|`/reg   <user> <pass>`|Register a new account \& log in|
|`/dm    <user> <msg>`|Send a private message|
|`/list`|List all online users|
|`/quit`|Disconnect gracefully|
|`<any text>`|Broadcast message to everyone|



## Key Features

* **Multi-client concurrency** — CachedThreadPool; one thread per client
* **Salted password hashing** — SHA-256 with SecureRandom 16-byte salt
* **Rate limiting** — 5 login attempts / 30 s; 20 messages / 5 s per IP
* **Private messages** — Direct message any online user
* **Security logging** — Every auth event written to `security.log`
* **Graceful disconnect** — Server notifies remaining clients on exit

