<!-- Improved compatibility of back to top link: See: https://github.com/othneildrew/Best-README-Template/pull/73 -->
<a name="readme-top"></a>
# kpdfx

<!-- ABOUT THE PROJECT -->

## About The Project

This is a JavaFX PDF-Viewer Control written in Kotlin.

[![Product Name Screen Shot][product-screenshot]](#about-the-project)

It is based on [PDFViewFX](https://github.com/dlsc-software-consulting-gmbh/PDFViewFX)

### Features

- mouse zooming/panning (with Cmd/Alt)
- viewport indicator in thumbnail
- panning in thumbnail
- page wise rotation (with thumbnail)

### Built With

* [![Java 11+][java.org]][java-url]
* [![Kotlin][kotlin.org]][kotlin-url]
* [![Gradle][gradle.org]][kotlin-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->

## Getting Started

### Prerequisites

* Java 11+
* Kotlin 1.8+

> This project builds with Java 11
> but 15, 17, 19 also works.

### Add dependency:

* Maven

```xml

<dependency>
   <groupId>de.confinitum</groupId>
   <artifactId>kpdfx</artifactId>
    <version>1.0.3</version>
</dependency>
```

* Gradle

```groovy
implementation 'de.confinitum:kpdfx:1.0.3'
```

### Code
Look at [PdfViewController](src/main/kotlin/com/confinitum/viewer/PdfViewController.kt)
for basic idea how to use this control.

* Kotlin
```kotlin
// create Control
val pdfView = PDFView().also {
   // tweak properties 
   it.setShowThumbnails(false)
   it.setThumbniailSize(150.0)
}
// add it to parent
somePane.children.add(pdfView)

// load pdf file
pdfView.load(file)
```

* Java

```java
PDFView pdfView=new PDFView();
        pdfView.setShowThumbnails(false);
        pdfView.setThumbnailSize(150.0);
// VBox.setVgrow(pdfView, Priority.ALWAYS);
        somePane.getChildren().add(pdfView);
        pdfView.load(file);
```

### Installation / Running Demo Application

1. Clone the repo
3. Run the Demo App

```sh
gradlew clean run
```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LICENSE -->

## License

Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>


<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->

[kotlin.org]: https://img.shields.io/badge/Kotlin-000000?style=for-the-badge&logo=kotlin

[kotlin-url]: https://kotlinlang.org

[gradle.org]: https://img.shields.io/badge/Gradle-000000?style=for-the-badge&logo=gradle

[gradle-url]: https://gradle.org

[java.org]: https://img.shields.io/badge/JavaFX-000000?style=for-the-badge&logo=java

[java-url]: https://java.org

[license-shield]: https://img.shields.io/github/license/github_username/repo_name.svg?style=for-the-badge

[license-url]: https://github.com/github_username/repo_name/blob/master/LICENSE.txt

[product-screenshot]: doc/screenshot.png
