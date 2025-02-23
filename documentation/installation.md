# Installation

--------------------------------------------------------------------------------

\[[Up](README.md)\] \[[Top](#top)\]

--------------------------------------------------------------------------------

## Table of Content

1. [Introduction](#introduction)
2. [Use Git](#use-git)
3. [Download Release](#download-release)
4. [Activate the Plugin](#activate-the-plugin)
5. [Intellij IDEA Hints](#intellij-idea-hints)

## Introduction

Depending on your setup and your plans, you can integrate this project in different ways.

* If the plugin is already bundled in your Blueprint (check the `plugin-descriptors.json` file of the Blueprint), you can immediately proceed with [Configure the Plugin](#configure-the-plugin).
* If the plugin is not bundled in your Blueprint, or you want to use a different release, select one from [Releases](https://github.com/CoreMedia/content-hub-adapter-rss/releases) and proceed with [Activate the Plugin](#activate-the-plugin).
* If you want to customize the plugin in your project, clone or fork the repository, and add or exchange the plugin in your Blueprint.
* If you do not want to use GitHub, proceed as described in [Download Release](#download-release).
* If you want to contribute a new feature or a bugfix, as an external developer, you need a fork of the repository to create a Pull Request.

## Use Git

Clone this repository or your fork. Make sure to use the suitable branch
for your workspace version (see [README](../README.md)). A fork is required if
you plan to customize the plugin.

Continue with [Activate the plugin](#activate-the-plugin).

## Download Release

Go to [Release](https://github.com/CoreMedia/content-hub-adapter-rss/releases) and download the version that matches your CMCC release version.
The ZIP file provides the Maven workspace of the plugin.

## Activate the Plugin

The rss contenthub adapter is a plugin for studio-server and studio-client.
The deployment of plugins is described in the [Blueprint Developer Manual](https://documentation.coremedia.com/cmcc-12/current/webhelp/coremedia-en/content/ApplicationPlugins.html).

In short, for a quick development roundtrip:
1. Possibly delete the plugin from the bundled plugins (as described in the "Using Plugin Descriptors and Releases" section of the Blueprint Developer Manual).
2. Build your Blueprint.
3. Build the `content-hub-adapter-rss`
   1. Run `mvn clean install` in the `studio-server` folder.

      Checkpoint: A zip file exists in `studio-server/target`. 
   2. Run `npm install -g pnpm@8.6 && pnpm install && pnpm -r run build && pnpm -r run package` in the folder `studio-client`.
  
      Checkpoint: A zip file exists in `studio-client/apps/main/content-hub-adapter-rss/build`.
4. Create a directory for studio-server plugins, e.g. `/tmp/studio-server-plugins`,
   and copy `content-hub-adapter-rss/studio-server/target/studio-server.content-hub-adapter-rss-<version>.zip`
   into that directory.
5. Start the studio server as usual, e.g. `mvn spring-boot:run`, with an additional property `-Dplugins.directories=/tmp/studio-server-plugins`
6. In your Blueprint studio-client workspace, there is a file `apps/studio-client/apps/main/app/jangaroo.config.js`,
   which contains a structure like
   ```
   module.exports = jangarooConfig({
     additionalPackagesDirs: [
       "./build/additional-packages",
     ],
     ...
   });
   ```
   Add the absolute path of the `content-hub-adapter-rss/studio-client/apps/main/content-hub-adapter-rss` directory
   (see step 2.2.) to the `additionalPackagesDirs` list.
6. Start the studio client as usual.

Now the plugin is running.  You won't yet notice it though, until you configure a connection
and restart the studio server.

## Configure the Plugin

Once having activated the plugin as described above, you can establish the connection to the external system by adding a Settings content to the site, globally, or the user's home folder. The general Content Hub configuration is described in the [Studio Developer Manual](https://documentation.coremedia.com/cmcc-10/artifacts/2107/webhelp/studio-developer-en/content/Content_HubAdapterConfiguration.html). Additional adapter-specific configuration is shown in the screenshot below:

![Image1: Adapter-specific configuration](images/configuration-rss.png)

## Intellij IDEA Hints

For the IDEA import:
- Ignore folder ".remote-package"
- Disable "Settings > Compiler > Clear output directory on rebuild"
