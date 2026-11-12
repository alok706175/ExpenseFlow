# ExpenseFlow &bull; A Personal Expense Tracker

A full-stack, production-ready Personal Expense Tracker Web Application built with **Java 17+**, **Spring Boot 3.x**, **Spring Data JPA**, **Hibernate**, **MySQL** (with **H2 in-memory database** for instant zero-config local testing), and a modern, responsive Single Page Interface powered by **Tailwind CSS** and **Chart.js**.

---

## 🚀 Key Features

- **Full CRUD Management**: Create, view, edit, and delete transactions with instant state refresh.
- **Financial Analytics & Charts**:
  - **KPI Summary Cards**: Total Income, Total Expenses, Net Balance, and Top Spending Category.
  - **Doughnut Chart**: Interactive visual breakdown of spending by category.
  - **Bar Chart**: 6-Month cashflow trend comparing Income vs. Expenses.
- **Budget Threshold Alerts**:
  - Configurable monthly spending budget.
  - Progress bar and dynamic alert banner with status flags:
    - 🟢 **OK**: Spending under 80%
    - 🟡 **WARNING**: Spending reached 80% – 100%
    - 🔴 **EXCEEDED**: Spending exceeded 100% of the set limit
- **Dynamic Search & Filtering**:
  - Instant debounced keyword search across transaction titles and notes.
  - Filter by date range (From Date & To Date), Category, and Type (INCOME/EXPENSE).
- **Data Export Utility**:
  - One-click export to a date-stamped CSV file reflecting all active filters.
- **API Documentation**:
  - Fully documented with **Springdoc OpenAPI (Swagger UI)** at `/swagger-ui.html`.
- **Zero-Setup Local Testing**:
  - Automatically runs with embedded H2 database by default. Pre-seeds demo records for instant exploration.

---

## 🛠️ Tech Stack & Architecture

- **Backend**: Java 21 / 17, Spring Boot 3.3.5
- **ORM / Persistence**: Spring Data JPA, Hibernate, HikariCP
- **Databases**: MySQL 8.x (Production) & H2 Database (Local Dev/Testing)
- **Validation**: Jakarta Validation API (`@Valid`, `@NotNull`, `@NotBlank`, etc.)
- **API Docs**: Springdoc OpenAPI 2.6.0 (Swagger 3)
- **CSV Engine**: OpenCSV 5.9
- **Frontend**: HTML5, Vanilla JavaScript (ES6+ Fetch API), Tailwind CSS (CDN), Chart.js
- **Containerization**: Docker Multi-stage build & Docker Compose

```
src/main/java/com/expensetracker/
├── ExpenseTrackerApplication.java
├── config/
│   ├── CorsConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── BudgetController.java
│   └── TransactionController.java
├── dto/
│   ├── BudgetDTO.java
│   ├── MonthlyTrendDTO.java
│   ├── SummaryDTO.java
│   ├── TransactionRequestDTO.java
│   └── TransactionResponseDTO.java
├── entity/
│   ├── Budget.java
│   ├── Transaction.java
│   └── TransactionType.java
├── exception/
│   ├── ErrorResponse.java
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
├── repository/
│   ├── BudgetRepository.java
│   └── TransactionRepository.java
└── service/
    ├── BudgetService.java
    ├── BudgetServiceImpl.java
    ├── TransactionService.java
    └── TransactionServiceImpl.java
```

---

## 🏃 Running the Application

### 1. Instant Local Run (H2 In-Memory Database)
By default, the application runs on the `h2` profile with zero setup required:
```bash
mvn spring-boot:run
```
Open your browser:
- **Web App**: [http://localhost:8080](http://localhost:8080)
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **H2 Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:expensedb`, User: `sa`, Password: *(leave blank)*)

### 2. Local Run with MySQL
1. Make sure MySQL is running and create database:
   ```sql
   CREATE DATABASE expense_tracker_db;
   ```
2. Run with MySQL profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=mysql
   ```
   *(Or set environment variables `DB_USERNAME`, `DB_PASSWORD`, `DB_URL`)*

### 3. Run with Docker Compose
To run both MySQL and the application together:
```bash
docker-compose up --build
```
Access the application at [http://localhost:8080](http://localhost:8080).

---

## 🌐 Deploying to Render.com with GitHub

1. **Push to GitHub**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of ExpenseFlow"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<repo-name>.git
   git push -u origin main
   ```

2. **Provision MySQL Database on Render**:
   - In Render Dashboard, click **New +** -> **MySQL** (or use any cloud MySQL like Aiven / PlanetScale).
   - Copy the database connection URL or connection details (Host, Port, User, Password, DB Name).

3. **Deploy Web Service on Render**:
   - In Render Dashboard, click **New +** -> **Web Service**.
   - Connect your GitHub repository.
   - Select **Docker** as the Environment / Runtime (Render automatically detects the `Dockerfile`).
   - Under **Environment Variables**, add:
     - `SPRING_PROFILES_ACTIVE`: `mysql`
     - `DB_URL`: `jdbc:mysql://<host>:<port>/<dbname>?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true`
     - `DB_USERNAME`: `<your_db_username>`
     - `DB_PASSWORD`: `<your_db_password>`
   - Click **Deploy Web Service**.
   - Render automatically assigns a live URL (e.g. `https://expense-tracker.onrender.com`). Render injects the `$PORT` variable which our Dockerfile and `application.properties` seamlessly bind to!

