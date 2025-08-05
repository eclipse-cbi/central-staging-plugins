<!--
    Copyright (c) 2025 Eclipse Foundation and contributors.
    This program and the accompanying materials are made available under the terms of the Eclipse Public License v. 2.0
    which is available at https://www.eclipse.org/legal/epl-2.0/
    SPDX-License-Identifier: EPL-2.0
-->

# Central Staging Plugins

Maven plugin for Sonatype Central Portal API ([API Doc](https://central.sonatype.com/api-doc)).

## Features
- Authenticate with Bearer token (from CLI or Maven settings.xml)
- Check if a component is published
- Retrieve deployment status
- Publish a deployment (only if validated)
- Maven plugin goals: `rc-list`, `rc-deployment-list`, `rc-release`, `rc-clean`

## Plugin Parameters

| Parameter              | Description                                                      | Default    | Required | Example Value                                  |
|------------------------|------------------------------------------------------------------|------------|----------|------------------------------------------------|
| central.bearerToken    | Bearer token for authentication                                  |            | Yes*     | xxxxxxxx...                                 |
| central.serverId       | Server id in settings.xml to use for bearer token                | central    | No       | myserverid                                     |
| central.namespace      | Namespace of the component                                      |            | Yes      | org.eclipse.cbi                                |
| central.name           | Name of the component                                           |            | Yes      | org.eclipse.cbi.tycho.example-parent           |
| central.version        | Version of the component                                        |            | Yes      | 1.0.0                                          |
| central.deploymentId   | Deployment id for release/clean operations                      |            | Yes*     | xxxxx-xxxxx-xxxx-xxx-xxxxxxx           |
| central.centralApiUrl  | Custom Central Portal API URL                                   | https://central.sonatype.com/api/v1/publisher | No       | https://central.sonatype.com/api/v1/publisher   |

You can provide your Bearer token either via the command line or securely via your Maven `settings.xml` file.

### Using settings.xml

Add the following to your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>unused</username>
      <password>YOUR_BEARER_TOKEN</password>
    </server>
  </servers>
</settings>
```


You can change the server id used in `settings.xml` by passing `-Dcentral.serverId=yourServerId` to any plugin command. Example:

```sh
mvn central-staging-plugins:rc-release -Dcentral.serverId=myserverid
```

The plugin will automatically use the token from the server with id `central` (or your custom id) if `-Dcentral.bearerToken` is not provided.


## Plugin Goals & Functions

| Goal/Function         | Description                                                      | Main Parameters                          | Example Command                                                    |
|-----------------------|------------------------------------------------------------------|------------------------------------------|-------------------------------------------------------------------|
| rc-list               | List publication status for a component                          | namespace, name, version, bearerToken     | mvn central-staging-plugins:rc-list -Dcentral.namespace=org.eclipse.cbi -Dcentral.name=org.eclipse.cbi.tycho.example-parent -Dcentral.version=1.0.0 |
| rc-release            | Release a deployment (publish if validated)                      | deploymentId (optional), bearerToken | mvn central-staging-plugins:rc-release -Dcentral.deploymentId=xxxxx-xxxxx-xxxx-xxx-xxxxxxx           |
| rc-clean              | Drop (delete) a deployment                                       | deploymentId, bearerToken                 | mvn central-staging-plugins:rc-clean -Dcentral.deploymentId=xxxxx-xxxxx-xxxx-xxx-xxxxxxx             |
| rc-deployment-list    | List all deployments for a namespace, with state, date, and errors per component | namespace, bearerToken                    | mvn central-staging-plugins:rc-deployment-list -Dcentral.namespace=org.eclipse.cbi |


## Build project

```sh
mvn clean package
```

## License

Eclipse Public License v. 2.0 ([LICENSE](LICENSE))

## Contributing

Contributions are welcome! Please submit issues and pull requests via GitHub.
