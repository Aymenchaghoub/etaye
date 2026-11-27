@description('Commit hash of the verified release.')
@minLength(40)
@maxLength(40)
param sourceSha string

@description('Hexadecimal registry manifest digest for the verified API image.')
@minLength(64)
@maxLength(64)
param apiDigest string

@description('Hexadecimal registry manifest digest for the verified cloud web image.')
@minLength(64)
@maxLength(64)
param webDigest string

@description('Direct Neon hostname for the approved demo branch.')
param neonDirectHost string

@description('IPv4 address approved for the initial HTTPS demonstration.')
param operatorIpv4 string

@secure()
@description('Password of the non-owner PostgreSQL runtime role.')
param runtimePassword string

@secure()
@description('Password of the non-owner PostgreSQL identity role.')
param identityPassword string

@secure()
@description('Visitor entry in the nginx password file, stored as a salted hash.')
param visitorHash string

resource environment 'Microsoft.App/managedEnvironments@2025-07-01' existing = {
  name: 'cae-etaye-demo'
}

var appName = 'ca-etaye-demo'
var host = '${appName}.${environment.properties.defaultDomain}'
var jdbcUrl = 'jdbc:postgresql://${neonDirectHost}:5432/etaye_demo_cloud?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require&connectTimeout=8&socketTimeout=10'
var packagePrefix = 'ghcr.io/aymenchaghoub/etaye'

resource application 'Microsoft.App/containerApps@2025-07-01' = {
  name: appName
  location: resourceGroup().location
  tags: union(resourceGroup().tags, {
    SourceSha: sourceSha
  })
  properties: {
    environmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      maxInactiveRevisions: 3
      secrets: [
        { name: 'db-runtime', value: runtimePassword }
        { name: 'db-identity', value: identityPassword }
        { name: 'visitor-auth', value: visitorHash }
      ]
      ingress: {
        external: true
        allowInsecure: false
        targetPort: 8081
        transport: 'http'
        ipSecurityRestrictions: [
          {
            name: 'demo-operator'
            action: 'Allow'
            ipAddressRange: '${operatorIpv4}/32'
          }
        ]
      }
    }
    template: {
      revisionSuffix: 'git-${substring(sourceSha, 0, 12)}'
      scale: {
        minReplicas: 0
        maxReplicas: 1
      }
      volumes: [
        {
          name: 'visitor-auth'
          storageType: 'Secret'
          secrets: [
            { secretRef: 'visitor-auth', path: 'visitors' }
          ]
        }
      ]
      containers: [
        {
          name: 'web'
          image: '${packagePrefix}-web@sha256:${webDigest}'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          volumeMounts: [
            { volumeName: 'visitor-auth', mountPath: '/run/etaye-secrets' }
          ]
          env: [
            { name: 'ETAYE_INGRESS_HOST', value: host }
          ]
          probes: [
            { type: 'Startup', tcpSocket: { port: 8081 }, periodSeconds: 2, timeoutSeconds: 2, failureThreshold: 60 }
            { type: 'Liveness', tcpSocket: { port: 8081 }, periodSeconds: 15, timeoutSeconds: 2, failureThreshold: 3 }
            { type: 'Readiness', httpGet: { port: 8082, path: '/ready' }, periodSeconds: 5, timeoutSeconds: 3, failureThreshold: 24 }
          ]
        }
        {
          name: 'api'
          image: '${packagePrefix}-api@sha256:${apiDigest}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'SERVER_ADDRESS', value: '127.0.0.1' }
            { name: 'SPRING_DATASOURCE_URL', value: jdbcUrl }
            { name: 'SPRING_DATASOURCE_USERNAME', value: 'etaye_runtime' }
            { name: 'SPRING_DATASOURCE_PASSWORD', secretRef: 'db-runtime' }
            { name: 'ETAYE_IDENTITY_PASSWORD', secretRef: 'db-identity' }
            { name: 'SPRING_FLYWAY_ENABLED', value: 'false' }
            { name: 'ETAYE_DEMO_ENABLED', value: 'false' }
            { name: 'ETAYE_DEMO_PATIENT_INPUT_ENABLED', value: 'false' }
            { name: 'ETAYE_WEB_ALLOWED_ORIGINS', value: 'https://${host}' }
            { name: 'SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE', value: '0' }
            { name: 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', value: '4' }
            { name: 'SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT', value: '9000' }
            { name: 'ETAYE_IDENTITY_DATASOURCE_HIKARI_MINIMUM_IDLE', value: '0' }
            { name: 'ETAYE_IDENTITY_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', value: '2' }
            { name: 'ETAYE_IDENTITY_DATASOURCE_HIKARI_CONNECTION_TIMEOUT', value: '9000' }
            { name: 'JAVA_TOOL_OPTIONS', value: '-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=112m -Xss256k' }
          ]
        }
      ]
    }
  }
}

output hostname string = application.properties.configuration.ingress.fqdn
