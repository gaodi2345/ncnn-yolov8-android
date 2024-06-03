# ncnn-yolov8-android

腾讯高性能神经网络前向计算框架——ncnn联合yolov8模型、OpenCV框架交叉编译移植到Android平台。

### 1、课题背景
本课题原本采用Android端采集实时画面帧，然后通过网络将画面帧传递到媒体服务器，服务器再用Python+Yolov8对画面帧做检测和识别，最后将结果返回给Android端去绘制目标检测结果。这样做最大的问题就是延时，经过局域网、4/5G网络测试，延时大概1-2s，此方案并不是最优解。为了优化（解决）此痛点，就必须将目标检测和识别移植到Android端，否则这个延时不可能会降下来。

### 2、解决方案
如题，采用 ncnn + yolov8 + opencv 三个框架实现这一目标

### 3、接下来详细介绍如何将这三个框架移植到Android端

#### 3.1、Android Studio 新建C++项目
注意，必须是C++项目，否则交叉编译环境会把人搞疯，不要走弯路了，步骤如下：
![微信截图_20240603170904.png](imags/微信截图_20240603170904.png)

![微信截图_20240603170934.png](imags/微信截图_20240603170934.png)

![微信截图_20240603170944.png](imags/微信截图_20240603170944.png)

图二根据自己熟悉的语言选择即可，不一定非得Kotlin(Java也是可以的)。至于其他的选项，建议按我的来，不然出问题了，会把人搞疯。

项目建完之后，按照Android Studio Giraffe版本的Android Studio会自动给你下载8.x版本的Gradle，高版本的Gradle的在Android Studio Giraffe上面不咋好使，咱改成本地的Gradle仓库路径（如果本地没有Gradle的，[点击下载](https://services.gradle.org/distributions/gradle-6.7.1-all.zip)），如下图：

![微信截图_20240603171618.png](imags/微信截图_20240603171618.png)

然后修改项目根目录下的 [settings.gradle](settings.gradle) 如下：
```gradle
rootProject.name = "Test"
include ':app'
```
注意，rootProject.name修改为自己的项目名。

再修改项目根目录下的 [build.gradle](build.gradle) 如下：
```gradle
// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    ext.kotlin_version = '1.6.10'
    repositories {
        maven { url 'http://maven.aliyun.com/nexus/content/groups/public/' }
        mavenCentral()
        google()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:4.2.2'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
}

allprojects {
    repositories {
        //阿里云镜像
        maven { url 'http://maven.aliyun.com/nexus/content/groups/public/' }
        //依赖库
        maven { url 'https://jitpack.io' }
        mavenCentral()
        google()
    }
}

tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
```
这个不用改，直接复制进去

再然后修改 app 目录下面的 [build.gradle](app/build.gradle) 如下：
```gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

android {
    compileSdkVersion 33

    defaultConfig {
        applicationId "com.casic.test"
        minSdkVersion 24
        targetSdkVersion 33
        versionCode 1000
        versionName "1.0.0.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }

    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.6.0'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    //基础依赖库
    implementation 'com.github.AndroidCoderPeng:Kotlin-lite-lib:1.0.10'
}
```
对着自己项目的修改此文件，dependencies里面的内容可以复制过去

然后点击OK，最后在主界面点击”Try Again“即可。到此，C++项目的的环境已经配置完毕，接下来修改Android的几个基本配置。

首先修改 [AndroidManifest.xml](app/src/main/AndroidManifest.xml) 如下：
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.casic.test">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Test">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
对着自己项目的修改此文件。

然后修改 [MainActivity.kt](app/src/main/java/com/pengxh/ncnn/yolov8/MainActivity.kt) 如下：
```Kotlin
class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {
    override fun initEvent() {

    }

    override fun initOnCreate(savedInstanceState: Bundle?) {

    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun observeRequestState() {

    }

    override fun setupTopBarLayout() {

    }
}
```
把原来自带的那部分代码全删了，用不到。

最后修改 [gradle.properties](gradle.properties) 如下：
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.enableJetifier=true
```
直接复制即可。然后点击”Sync Now“

最后点击顶部三角形，如果能运行，就表示已经完全完成C++项目配置。


最后感谢各个开源者的辛勤付出（排名不分先后）：

* [ncnn](https://github.com/Tencent/ncnn)
  ：腾讯开源的一个为手机端极致优化的高性能神经网络前向计算框架，无第三方依赖，跨平台，ncnn主要基于C++和caffe。
* [yolov8](https://github.com/ultralytics/ultralytics): YOLO(You Only Look
  Once）是一种流行的物体检测和图像分割模型，由华盛顿大学的约瑟夫-雷德蒙（Joseph Redmon）和阿里-法哈迪（Ali
  Farhadi）开发，YOLO 于 2015 年推出。
* [OpenCV](https://github.com/opencv/opencv): OpenCV（open source computer vision library）是一个基于BSD许可（开源）发行的跨平台计算机视觉库，可以运行在Linux、Windows、Android和Mac OS操作系统上。