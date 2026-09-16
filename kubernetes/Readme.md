# Kubernetes deployment

**NOTE** Knowledge and experience with kubernetes are required and is assumed.

This will not covers how to get SSL certificates (Letsencrypt) neither than the deployment of an Ingress Controller (Haproxy/Traefik/Nginx).

## Prerequisites

These manifests deploy Elasticsearch 9.5.2 alongside Snowstorm. Before applying them:

- Kubernetes 1.29 or later is required. Elasticsearch runs as a native sidecar (an initContainer with `restartPolicy: Always`), which reached GA in 1.29. On an older cluster the API server rejects that field.

- The pod runs Elasticsearch with a 4g heap and Snowstorm with up to 4g, so schedule it onto a node with at least 10Gi of memory free. No `resources` requests or limits are set, so Kubernetes will place the pod on any node and neither container is capped — add a `resources` block to `snowstorm-deploy.yml` if your cluster needs one, keeping the limits above the `ES_JAVA_OPTS` and `JAVA_TOOL_OPTIONS` heap sizes.

- Each node running the Elasticsearch pod needs `vm.max_map_count` set to at least `262144`, otherwise the container will fail to start:

  ```bash
  sysctl -w vm.max_map_count=262144
  ```

- The host directory backing `elasticsearch-data` must be writable by uid `1000`, the user the Elasticsearch image runs as:

  ```bash
  chown -R 1000:1000 /srv/production/snowstorm-elasticsearch-data
  ```

- Upgrading an existing deployment from Elasticsearch 8.x? Follow the [Elasticsearch 9 upgrade guide](../docs/elasticsearch9-upgrade.md) first. Elasticsearch 9 cannot start against a data directory left by a cluster that was not upgraded through the documented steps, and Snowstorm 12 cannot talk to an 8.x server.

- `ingress-rules.yml` has `ingressClassName` commented out. Set it to the ingress controller you have installed, unless your cluster has a default class.

First of all, you have to build your own docker images than push it to your own docker registry or use the docker hub. Depending of your kubernetes cluster you might want to change the location of where the elasticsearch data will be stored (actually HostPath storage model)

`kubectl create -f snowstorm-deploy.yml -n production`

Now you want to create the secret where your certificates will be stored in order to be used by the ingress controller.

`kubectl create secret tls snowstorm.example.com --key ./snowstorm.example.com.key --cert ./snowstorm.example.com.fullchain -n production`

Finally, we use this file ```ingress-rules``` to declare our hosts with his url and SSL certificates without authentication.

`kubectl create -f ingress-rules.yml -n production`

The namespace matters: an Ingress can only route to Services in its own namespace, so one created in `default` would not find `snowstorm-front` and would serve 503.

This will create ingress rules that your Ingress Controller will apply.
