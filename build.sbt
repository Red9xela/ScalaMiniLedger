scalaVersion := "3.10.0"

lazy val root = rootProject
  .settings(
    name := "ScalaMiniLedger",
    libraryDependencies ++= Seq(
      //You can add library dependencies here, for example,
      //"org.scalatest" %% "scalatest" % "3.2.19" % Test,
      //"org.scalameta" %% "munit" % "1.2.3" % Test
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    )
  )
