plugins { kotlin("jvm") version "2.2.10" }
repositories { mavenCentral() }
dependencies { testImplementation("junit:junit:4.13.2") }
kotlin {
  jvmToolchain(17)
  sourceSets {
    main {
      kotlin.srcDir("../../modules/app-lock/android/src/main/java")
      kotlin.include("**/LockEngine.kt", "**/PlatformPolicy.kt", "**/GracePolicy.kt", "**/PinCredential.kt", "**/ManagementSession.kt")
    }
    test {
      kotlin.srcDir("../../modules/app-lock/android/src/test/java")
      kotlin.include("**/LockEngineTest.kt", "**/PlatformPolicyTest.kt", "**/PinCredentialTest.kt", "**/ManagementSessionTest.kt")
    }
  }
}
tasks.test { useJUnit() }
