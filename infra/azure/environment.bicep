param location string
param tags object

resource demo 'Microsoft.App/managedEnvironments@2025-07-01' = {
  name: 'cae-etaye-demo'
  location: location
  tags: tags
  properties: {
    // Live system and container log streaming remain available without persistent ingestion.
    appLogsConfiguration: {
      destination: null
    }
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
    zoneRedundant: false
  }
}

output resourceId string = demo.id
