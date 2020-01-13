[![Build status](https://github.com/ViceIce>/jobcacher-plugin/workflows/Java%20CI/badge.svg)](https://github.com/ViceIce/jobcacher-plugin/actions?query=workflow%3A"Java+CI")

Job Cacher Plugin
=================

This plugin was created to improve build performance for builds that utilize executors that start from a clean
image each time such as docker based executors.  This plugin was inspired by the caching capability of TravisCI.

## Features

- [x] Item storage extension point supporting on master storage and AWS S3
- [x] Cache Wrapper for free style jobs that manages the cache
- [x] Implements Arbitrary File Cache where user specifies paths to be cached
- [x] UI on Job page to review the job's caches
- [x] Supports Pipeline jobs with a cache block
- [x] Cache Extension Point for other plugins to provide opinionated caching capability such as Gradle caches
