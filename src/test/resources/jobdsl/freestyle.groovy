freeStyleJob('freestyle') {
    wrappers {
        jobcacher {
            caches {
                arbitraryFileCache {
                    path('node_modules')
                    includes('**/*')
                    excludes(null)
                }
                arbitraryFileCache {
                    path('.m2/repository')
                    includes('**/*')
                    excludes(null)
                }
            }
            skipRestore(false)
            skipSave(true)
            defaultBranch('main')
            maxCacheSize(1024L)
        }
    }
    steps {
        shell('ls -lah')
    }
}
