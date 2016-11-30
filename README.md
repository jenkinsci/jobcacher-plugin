# Job Cacher Plugin

This plugin was created to improve build performance for builds that utilize executors that start from a clean
image each time such as docker based executors.  This plugin was inspired by the caching capability of TravisCI.

## Features

- [x] Creates a caching capability for executors that start fresh each build
- [x] Implements Arbitrary File Cache where user specifies paths to be cached
- [x] UI on Job page to review the job's caches
- [x] Supports Pipeline jobs
- [x] Cache Extension Point for other plugins to provide opinionated caching capability such as Gradle caches