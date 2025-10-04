# jvm-live-reload

[![Build Status](https://github.com/seroperson/jvm-live-reload/actions/workflows/build.yml/badge.svg)](https://github.com/seroperson/jvm-live-reload/actions/workflows/build.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.seroperson/sbt-jvm-live-reload_2.12)](https://mvnrepository.com/artifact/me.seroperson/sbt-jvm-live-reload_2.12)
![Maven Central Last Update](https://img.shields.io/maven-central/last-update/me.seroperson/sbt-jvm-live-reload_2.12)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/seroperson/jvm-live-reload/LICENSE)

<!-- prettier-ignore-start -->
> [!WARNING] 
> This project is in an alpha-quality stage. I haven't used it in
> production yet, however it should work okay. Everything tends to change.
<!-- prettier-ignore-end -->

This project aims at providing consistent "Live Reload" experience for any web
application on JVM. It allows you to speedup your development cycle no matters
what framework or library you're using.

This repository is managed using [jj][9] (jujutsu) VCS.

## Installation

To get started with, firstly you have to install plugin for your build system.
Currently supported build systems are: `sbt`, `gradle`, `mill`. We want to cover
as much as we can, so probably more will come later.

## License

```text
MIT License

Copyright (c) 2025 Daniil Sivak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

[1]: https://github.com/pac4j/pac4j
[2]: https://github.com/zio/zio-http
[3]: https://www.pac4j.org/docs/index.html
[4]: https://www.pac4j.org/docs/main-concepts-and-components.html
[5]:
  https://github.com/pac4j/pac4j/blob/632413e40d47fe0955abd8c1610c88badc214c4a/pac4j-core/src/main/java/org/pac4j/core/authorization/authorizer/IsRememberedAuthorizer.java
[6]:
  https://github.com/pac4j/pac4j/tree/632413e40d47fe0955abd8c1610c88badc214c4a/pac4j-core/src/main/java/org/pac4j/core/authorization/authorizer
[7]: https://github.com/pac4j/http4s-pac4j
[8]: https://github.com/pac4j/play-pac4j/
[9]: https://github.com/jj-vcs/jj
[10]: https://seroperson.me/2025/09/03/zio-http-jwt-auth/
