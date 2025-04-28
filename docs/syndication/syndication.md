# Syndication Documentation

This document serves as the **entry point** to explain the syndication functionality of the Snowstorm-based application.  
The following documentation pages each describe a specific aspect of the syndication mechanism:

- **syndication-terminologies.md** — Overview of the supported terminologies
- **syndication-on-startup.md** — How to apply syndication during application startup
- **syndication-on-runtime.md** — How to apply syndication after the application has started
- **syndication-with-docker.md** — How to configure syndication with Docker

---

## Notes & Best Practices

- ✅ The application **avoids re-importing** terminology versions that have already been successfully loaded.
- 🔐 **Never commit** `.env` files or credentials to version control repositories.
- 📉 Imports are triggered **only if** the requested version has not yet been imported successfully.
- 🔎 Use the `GET /syndication/status` endpoint to **monitor import progress** and **troubleshoot errors**.
- 💡 The provided `docker-compose.yml` file is intended as an **example configuration** and may need to be adapted for production use.

