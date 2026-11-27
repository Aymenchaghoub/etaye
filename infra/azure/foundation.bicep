targetScope = 'subscription'

@description('Azure region permitted by the subscription policy and confirmed to have Consumption quota.')
@allowed([
  'germanywestcentral'
  'norwayeast'
  'belgiumcentral'
  'polandcentral'
  'italynorth'
])
param location string

var tags = {
  Project: 'etaye'
  Environment: 'demo'
  Purpose: 'synthetic-portfolio'
  ManagedBy: 'infrastructure-as-code'
}

resource demoGroup 'Microsoft.Resources/resourceGroups@2025-04-01' = {
  name: 'rg-etaye-demo'
  location: location
  tags: tags
}

module demoEnvironment './environment.bicep' = {
  name: 'etaye-demo-environment'
  scope: demoGroup
  params: {
    location: location
    tags: tags
  }
}

output environmentResourceId string = demoEnvironment.outputs.resourceId
