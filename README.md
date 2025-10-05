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
of what framework or library you're using.

This repository is managed using [jj][1] (jujutsu) VCS.

## Installation

To get started with, firstly you have to install plugin for your build system.
Currently supported build systems are: `sbt`, `gradle`. We want to cover as much
as we can, so probably more build systems will come later.

## How it works

The core principle is wide-known and was already adopted by such gigants as
Spring Boot, quarkus, Play, Apache Tapestry (and probably more). Basically all
of them work like that:

- Starting an application.
- Watching for project changes.
- When change occurs, application (but not the JVM itself) stops, underlying
  `ClassLoader` got dropped.
- Application starts again with new modified `ClassLoader`.

This approach allows you to boost your development cycle by saving time on JVM
starup and system classes initialization. Concrete frameworks can also use some
additional boost depending on their own structure and lifecycle.

This project implements Play-like live reloading and intended to be used only
with web applications, so you can't (yet?) use it for daemons. It means that in
case of new changes reloading triggers only when some endpoint was called, not
instantly right after a change occurs.

## Implementation details

Basically this project works like this:

- When `run` task is called, it starts the reverse-proxy webserver.
- This proxy starts your underlying application and routes everything into it.
- When change occurs, next request to the proxy will reload an underlying code
  by re-creating a `ClassLoader` and stopping/starting an underlying
  application.

Intermediate proxy is required to make this solution universal.

The very important thing to make it universal are so-called startup and shutdown
hooks. They define how to start and shutdown your application. When reloading
occurs, the proxy will call all defined shutdown hooks to stop it, and then
it'll call all startup hooks to start it again. Both types of hooks are
blocking. When shutdown hooks are finished, it's considered that application is
stopped and all its' resources are cleaned. At the same time when startup hooks
are finished, it's considered that application is ready to receive requests.

For example, there is the builtin `RestApiHealthCheckStartupHook`, which polls
`/health` endpoint until success response. It means that your application will
be considered started when its' `/health` returned `200`. At the same time there
is a `RestApiHealthCheckShutdownHook`, which polls the endpoint until failure.

## License

A lot of code was initially copied from the [playframework][2] project. Many
thanks for all the contributors, as without them it would take much more time to
implement everything correctly.

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

[1]: https://github.com/jj-vcs/jj
[2]: https://github.com/playframework/playframework
