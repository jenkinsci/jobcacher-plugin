# Job Cacher Plugin

[![Build Status](https://ci.jenkins.io/job/Plugins/job/jobcacher-plugin/job/main/badge/icon)](https://ci.jenkins.io/job/Plugins/job/jobcacher-plugin/job/main/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/jobcacher.svg)](https://plugins.jenkins.io/jobcacher)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/jobcacher.svg?color=blue)](https://plugins.jenkins.io/jobcacher)

## Introduction

This plugin provides caching for dependencies and build artifacts to reduce build execution times.
This is especially useful for Jenkins setups with ephemeral executors, which always start from a clean state, such as container based ones.

> [!WARNING]
> Users upgrading from version `640.v424a_7cc1087a_` and above AND using S3 storage MUST install extension plugin `https://github.com/jenkinsci/s3-jobcacher-storage-plugin`
>
> Configuration is kept in the same place, but the plugin will not work with S3 storage without this extension.
>
> Users using controller storage or other storage types are not affected and does not need to install this extension.

### Features

- Store caches on the Jenkins controller
- Use caching in pipeline and freestyle jobs
- Define maximum cache sizes so that the cache won't grow indefinitely
- View job specific caches on job page

### Extension Points

- `jenkins.plugins.itemstorage.ItemStorage` for adding custom cache storages
- `jenkins.plugins.jobcacher.Cache` for adding custom caches

Other known consumer plugins that implement the `jenkins.plugins.itemstorage.ItemStorage` extension point to provide other backend storage options:

- [jobcacher-artifactory-storage](https://plugins.jenkins.io/jobcacher-artifactory-storage/)
- [jobcacher-azure-storage](https://plugins.jenkins.io/jobcacher-azure-storage/)
- [jobcacher-s3-storage](https://plugins.jenkins.io/s3-jobcacher-storage//)
- [jobcacher-oras-storage](https://plugins.jenkins.io/jobcacher-oras-storage/)

## Configuration

### Global Configuration Options

By default, the plugin is configured to use on-controller storage for the cache.
In addition, a storage implementation for Amazon S3 and S3 compatible services is also available.

The storage type can be configured in the global configuration section of Jenkins.

### Cache Configuration Options

The following cache configuration options apply to all supported job types.

| Option          | Mandatory | Description                                                                                                                                                                                                                                                                                                                |
|-----------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `maxCacheSize`  | no        | The maximum size in megabytes of all configured caches that Jenkins will allow until it deletes all completely and starts the next build from an empty cache. This prevents caches from growing indefinitely with the downside of periodic fresh builds without a cache. Set to zero or empty to skip checking cache size. |
| `skipSave`      | no        | If set to `true`, skip saving the cache. Default `false`                                                                                                                                                                                                                                                                   |
| `skipRestore`   | no        | If set to `true`, skip restoring the cache. Default `false`                                                                                                                                                                                                                                                                |
| `defaultBranch` | no        | If the current branch has no cache, it will seed its cache from the specified branch. Leave empty to generate a fresh cache for each branch.                                                                                                                                                                               |
| `caches`        | yes       | Defines the caches to use in the job (see below).                                                                                                                                                                                                                                                                          |

### `ArbitraryFileCache`

| Option                      | Mandatory | Default value | Description                                                                                                                                                                                                                |
|-----------------------------|-----------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `path`                      | yes       |               | The path to cache. It can be absolute or relative to the workspace.                                                                                                                                                        |
| `cacheName`                 | no        |               | The name of the cache. Useful if caching the same path multiple times in a pipeline.                                                                                                                                       |
| `includes`                  | no        | `**/*`        | The pattern to match files that should be included in caching.                                                                                                                                                             |
| `excludes`                  | no        |               | The pattern to match files that should be excluded from caching.                                                                                                                                                           |
| `useDefaultExcludes`        | no        | `true`        | Whether to use default excludes (see [DirectoryScanner.java#L170](https://github.com/apache/ant/blob/eeacf501dd15327cd300ecd518284e68bb5af4a4/src/main/org/apache/tools/ant/DirectoryScanner.java#L170) for more details). |
| `cacheValidityDecidingFile` | no        |               | The workspace-relative path to one or multiple files which should be used to determine whether the cache is up-to-date or not. Only up-to-date caches will be restored and only outdated caches will be created.           |
| `compressionMethod`         | yes       | `TARGZ`       | The compression method (`ZIP`, `TARGZ`, `TARGZ_BEST_SPEED`, `TAR_ZSTD`, `TAR`) to use. Some are without compression. **Note that method `NONE` is not supported anymore and is now treated as `TARGZ`.**                   |

### Fine-tuning cache validity

The `cacheValidityDecidingFile` option can be used to fine-tune the cache validity. At its simplest,
you specify a file and the cache will be considered outdated if the file changes. You can also specify
a folder, in which case all the files in the folder (recursively found) will be used to determine the
cache validity. This can be too coarse if you have generated files lumped in with source files. To
fine-tune this, you can specify an arbitrary list of patterns to include and exclude paths from the
cache validity check. The patterns are relative to the workspace root. These patterns are paths
or glob patterns, separated by commas. Exclude patterns start with the `!` character. The order of
the patterns does not matter. You can mix include and exclude patterns freely.

For example, to cache everything in a folder `src` except files named `*.generated`:

```
arbitraryFileCache(
    path: 'my-cache',
    cacheValidityDecidingFile: 'src,!src/**/*.generated',
    includes: '**/*',
    excludes: '**/*.generated'
)
```

### Choosing the compression method

Different situations might require different packaging and compression methods, controlled by the `compressionMethod` option.

`TARGZ` use gzip with a compression level which is a "sweet spot" between compression speed and size (Deflate compression level 6).
If you cache lots of text files, for instance source code or `node_modules`-directories in Javascript-builds, this is a good choice.

`TARGZ_BEST_SPEED` use gzip with the lowest compression level, for best throughput.
If high speed at cache creation is important, and you cache directories with a mix of both text and binary files, this option might be a good choice.

`TAR` use no compression.
If you cache directories with lots of binary files, this option might be best.

`TAR_ZSTD` use a [JNI-binding to machine architecture dependent Zstandard binaries](https://github.com/luben/zstd-jni), with pre-built binaries for many architectures are available.
It offers better compression speed and ratio than gzip.

`ZIP` packages the cache in a zip archive.

## Usage in Jobs

### Freestyle Jobs

The plugin provides a "Job Cacher" build environment.
The cache(s) will be restored at the start of the build and updated at the end of the build.

### Pipeline Jobs

The plugin provides a `cache` build step that can be used within the pipeline definition.
The cache(s) will be restored before calling the closure and updated after executing it.

```groovy
cache(maxCacheSize: 250, defaultBranch: 'develop', caches: [
        arbitraryFileCache(path: 'node_modules', cacheValidityDecidingFile: 'package-lock.json')
]) {
    // ...
}
```

#### Note about using within Docker containers
If you use the plugin within a Docker container through the [Docker Pipeline plugin](https://plugins.jenkins.io/docker-workflow/), the path to cache must be located within the workspace. Everything outside is not visible to the plugin and therefore not cacheable.

## Contributing

See [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## Changelog

See [releases](https://github.com/jenkinsci/jobcacher-plugin/releases)
