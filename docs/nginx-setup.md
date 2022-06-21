# Snowstorm Nginx Setup
SNOMED International uses Nginx as a reverse proxy server in front of Snowstorm.
Nginx handles CORS headers and SSL certificates and can forward requests to Snowstorm or serve static content like the SNOMED CT Browser.

This is a basic Nginx configuration to forward HTTP requests from port 80 to Snowstorm (running on port 8080):
```
server {
  listen 80;
  server_name  localhost;
  
  gzip on;
  gzip_types application/javascript;
  
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Forwarded-Host $host;
  proxy_connect_timeout 150;
  proxy_send_timeout 100;
  proxy_read_timeout 100;
  proxy_buffers 4 32k;
  proxy_busy_buffers_size    64k;
  proxy_temp_file_write_size 64k;
  # Large client body to support RF2 upload
  client_max_body_size 1024m;
  client_body_buffer_size 128k;

  # Make the FHIR API available as http://localhost/fhir
  location /fhir {
    # HAPI FHIR library does not seem to need any additional proxy headers.
    proxy_pass http://localhost:8080/fhir;
  }

  # Make the Native API (for SNOMED CT) available as http://localhost/snomed
  location /snomed {
    # Spring Boot requires port and prefix headers.
    proxy_set_header X-Forwarded-Port 80;
    proxy_set_header X-Forwarded-Prefix /snomed;
    proxy_pass http://localhost:8080/;
  }
}
```
## SSL Certificate Setup
[certbot](https://certbot.eff.org/) automates the process of generating a free SSL certificate (using [LetsEncrypt](https://letsencrypt.org/)) and installing it into Nginx.
See [certbot instructions for Ubuntu and Nginx](https://certbot.eff.org/instructions?ws=nginx&os=ubuntufocal). Proxy headers may need adjusting after this.
