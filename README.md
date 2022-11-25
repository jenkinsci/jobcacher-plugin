# Job Cacher Plugin

[![Build Status](https://ci.jenkins.io/job/Plugins/job/jobcacher-plugin/job/main/badge/icon)](https://ci.jenkins.io/job/Plugins/job/jobcacher-plugin/job/main/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/jobcacher.svg)](https://plugins.jenkins.io/jobcacher)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/jobcacher.svg?color=blue)](https://plugins.jenkins.io/jobcacher)

## Introduction

This plugin provides caching for dependencies and build artefacts to reduce build execution times.
This is especially useful for Jenkins setups with ephemeral executors which always start from a clean state, such as container based ones.

### Features

- Store caches on the Jenkins controller, AWS S3 and S3 compatible services
- Use caching in pipeline and freestyle jobs
- Define maximum cache sizes so that the cache won't grow indefinitely
- View job specific caches on job page

### Extension Points

- `jenkins.plugins.itemstorage.ItemStorage` for adding custom cache storages
- `jenkins.plugins.jobcacher.Cache` for adding custom caches

## Configuration

### Global Configuration Options

By default, the plugin is configured to use on-controller storage for the cache.
In addition, a storage implementation for Amazon S3 and S3 compatible services is also available.

The storage type can be configured in the global configuration section of Jenkins.

### Cache Configuration Options

The following cache configuration options apply to all supported job types.

| Option          | Mandatory | Description                                                                                                                                                                                                                                                              |
|-----------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maxCacheSize`  | yes       | The maximum size in megabytes of all configured caches that Jenkins will allow until it deletes all completely and starts the next build from an empty cache. This prevents caches from growing indefinitely with the downside of periodic fresh builds without a cache. |    
| `defaultBranch` | no        | If the current branch has no cache, it will seed its cache from the specified branch. Leave empty to generate a fresh cache for each branch.                                                                                                                             |
| `caches`        | yes       | Defines the caches to use in the job (see below).                                                                                                                                                                                                                        |

### `ArbitraryFileCache`

| Option                      | Mandatory | Default value | Description                                                                                                                                                                                                                                |
|-----------------------------|-----------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `path`                      | yes       |               | The path to cache. It can be absolute or relative to the workspace.                                                                                                                                                                        |
| `cacheName`                 | no        |               | The name of the cache. Useful if caching the same path multiple times in a pipeline.                                                                                                                                                       |
| `includes`                  | no        | `**/*`        | The pattern to match files that should be included in caching.                                                                                                                                                                             |
| `excludes`                  | no        |               | The pattern to match files that should be excluded from caching.                                                                                                                                                                           |
| `useDefaultExcludes`        | no        | `true`        | Whether to use default excludes (see [DirectoryScanner.java#L170](https://github.com/apache/ant/blob/eeacf501dd15327cd300ecd518284e68bb5af4a4/src/main/org/apache/tools/ant/DirectoryScanner.java#L170) for more details).                 |
| `cacheValidityDecidingFile` | no        |               | The workspace-relative path to one or multiple (by using a glob pattern) files which should be used to determine whether the cache is up-to-date or not. Only up-to-date caches will be restored and only outdated caches will be created. |
| `compressionMethod`         | yes       | `NONE`        | The compression method (`NONE`, `ZIP`, `TARGZ`, `TAR_ZSTD`) to use.                                                                                                                                                                        |

## Usage in Jobs

### Freestyle Jobs

The plugin provides a "Job Cacher" build environment.
The cache(s) will be restored at the start of the build and updated at the end of the build.

### Pipeline Jobs

The plugin provides a `cache` build step that can be used within the pipeline definition.
The cache(s) will be restored before calling the closure and updated after executing it.

```groovy
cache(maxCacheSize: 250, defaultBranch: 'develop', caches: [
        arbitraryFileCache(path: 'node_modules', cacheValidityDecidingFile: 'package-lock.json', compressionMethod: 'TARGZ')
]) {
    // ...
}
```

## Contributing

See [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## Changelog

See [releases](https://github.com/jenkinsci/jobcacher-plugin/releases)
