<!--
    Copyright (c) 2025 Eclipse Foundation and contributors.
    This program and the accompanying materials are made available under the terms of the Eclipse Public License v. 2.0
    which is available at https://www.eclipse.org/legal/epl-2.0/
    SPDX-License-Identifier: EPL-2.0
-->

# Central Staging Plugins

Maven plugin for Sonatype Central Portal API ([API Doc](https://central.sonatype.com/api-doc)).

- [Central Staging Plugins](#central-staging-plugins)
  - [Features](#features)
  - [Plugin Parameters](#plugin-parameters)
  - [Plugin Goals](#plugin-goals)
  - [Authenticate](#authenticate)
  - [Github Integration Example](#github-integration-example)
  - [Build project](#build-project)
  - [License](#license)
  - [Contributing](#contributing)


## Features

- Authenticate with Bearer token (from CLI or Maven settings.xml)
- Check if a component is published in Central
- Retrieve and display deployment status (state, errors, date)
- List all deployments for a namespace, including state, creation date, and errors per component
- Release (publish) a deployment if it is validated
- Drop (delete) a deployment by ID
- Drop all deployments in a namespace with `central.removeAll`
- Drop only deployments in FAILED state with `central.removeFailedOnly` (works with single, latest, or all deployments)
- Supports custom Central Portal API URL and serverId for token retrieval


## Plugin Parameters

| Parameter                | Description                                                                                        | Default                                       | Example Value                                 |
| ------------------------ | -------------------------------------------------------------------------------------------------- | --------------------------------------------- | --------------------------------------------- |
| central.bearerToken      | Bearer token for authentication                                                                    |                                               | xxxxxxxx...                                   |
| central.serverId         | Server id in settings.xml to use for bearer token                                                  | central                                       | myserverid                                    |
| central.namespace        | Namespace of the component                                                                         |                                               | org.eclipse.cbi                               |
| central.name             | Name of the component                                                                              |                                               | org.eclipse.cbi.tycho.example-parent          |
| central.version          | Version of the component                                                                           |                                               | 1.0.0                                         |
| central.deploymentId     | Deployment id for release/drop operations                                                         |                                               | xxxxx-xxxxx-xxxx-xxx-xxxxxxx                  |
| central.centralApiUrl    | Custom Central Portal API URL                                                                      | https://central.sonatype.com/api/v1/publisher | https://central.sonatype.com/api/v1/publisher |
| central.removeAll        | If true, drop all deployments in the namespace                                                     | false                                         | true                                          |
| central.removeFailedOnly | If true, only drop deployments in FAILED state (used with removeAll or when dropping by id/latest) | true                                          | true                                          |
| central.dryRun           | If true, only simulate the release/drop (no action performed)                                     | false                                         | true                                          |

You can provide your Bearer token either via the command line or securely via your Maven `settings.xml` file.

## Plugin Goals

| Goal/Function | Description                                                                      | Main Parameters                              | Example Command                                                                                                                                       |
| ------------- | -------------------------------------------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| rc-status     | List publication status for a component                                          | namespace, name, version, bearerToken        | mvn central-staging-plugins:rc-status -Dcentral.namespace=org.eclipse.cbi -Dcentral.name=org.eclipse.cbi.tycho.example-parent -Dcentral.version=1.0.0 |
| rc-release    | Release a deployment (publish if validated, supports dry run)                    | deploymentId (optional), bearerToken, dryRun | mvn central-staging-plugins:rc-release -Dcentral.deploymentId=xxxxx-xxxxx-xxxx-xxx-xxxxxxx -Dcentral.dryRun=true                                      |
| rc-drop      | Drop (delete) a deployment (supports dry run)                                    | deploymentId, bearerToken, dryRun            | mvn central-staging-plugins:rc-drop -Dcentral.deploymentId=xxxxx-xxxxx-xxxx-xxx-xxxxxxx -Dcentral.dryRun=true                                        |
| rc-list       | List all deployments for a namespace, with state, date, and errors per component | namespace, bearerToken                       | mvn central-staging-plugins:rc-list -Dcentral.namespace=org.eclipse.cbi                                                                               |

## Authenticate 

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

How to produce a bearer token?

```shell
$ printf "example_username:example_password" | base64
XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

## Github Integration Example


```yaml
name: Publish staging package to the Maven Central Repository
on:
  push:
    branches: [ "staging" ]
  workflow_dispatch:

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Maven Central Repository
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          server-id: central
          server-username: MAVEN_USERNAME
          server-password: MAVEN_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: MAVEN_GPG_PASSPHRASE
      - name: Generate Sonatype Bearer Token
        id: bearer_token
        run: |
          echo "CENTRAL_SONATYPE_TOKEN=$(echo -n '${{ secrets.CENTRAL_SONATYPE_TOKEN_USERNAME }}:${{ secrets.CENTRAL_SONATYPE_TOKEN_PASSWORD }}' | base64)" >> $GITHUB_ENV
      - name: Drop failed staging deployments
        run: |        
          mvn -P central-staging --batch-mode central-staging-plugins:rc-drop -Dcentral.removeAll=true
        env:
          MAVEN_PASSWORD: ${{ env.CENTRAL_SONATYPE_TOKEN }}
      - name: Publish package
        run: |
          mvn -P central-staging --batch-mode clean compile javadoc:jar deploy -DskipTests
        env:
          MAVEN_USERNAME: ${{ secrets.CENTRAL_SONATYPE_TOKEN_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.CENTRAL_SONATYPE_TOKEN_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
      - name: Release package
        run: |        
          mvn -P central-staging --batch-mode central-staging-plugins:rc-release
        env:
          MAVEN_PASSWORD: ${{ env.CENTRAL_SONATYPE_TOKEN }}
```

## Build project

```sh
mvn clean package
```

## License

Eclipse Public License v. 2.0 ([LICENSE](LICENSE))

## Contributing

Contributions are welcome! Please submit issues and pull requests via GitHub.
