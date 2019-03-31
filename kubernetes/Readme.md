# Kubernetes deployment

**NOTE** Knowledge and experience with kubernetes are required and is assumed.

This will not covers how to get SSL certificates (Letsencrypt) neither than the deployment of an Ingress Controller (Haproxy/Traefik/Nginx).

First of all, you have to build your own docker images than push it to your own docker registry or use the docker hub. Depending of your kubernetes cluster you might want to change the location of where the elasticsearch data will be stored (actually HostPath storage model)

`kubectl create -f snowstorm-deploy.yml -n production`

Now you want to create the secret where your certificates will be stored in order to be used by the ingress controller.

`kubectl create secret tls snowstorm.example.com --key ./snowstorm.example.com.key --cert ./snowstorm.example.com.fullchain -n production`

Finally, we use this file ```ingress-rules``` to declare our hosts with his url and SSL certificates without authentication.

`kubectl create -f ingress-rules.yml`

This will create ingress rules that your Ingress Controller will apply.
