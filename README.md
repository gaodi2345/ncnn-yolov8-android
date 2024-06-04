# ncnn-yolov8-android

腾讯高性能神经网络前向计算框架——ncnn联合yolov8模型、OpenCV框架交叉编译移植到Android平台。

### 1、课题背景

本课题原本采用Android端采集实时画面帧，然后通过网络将画面帧传递到媒体服务器，服务器再用Python+Yolov8对画面帧做检测和识别，最后将结果返回给Android端去绘制目标检测结果。这样做最大的问题就是延时，经过局域网、4/5G/WiFi网络测试，延时大概1-2s，此方案并不是最优解。为了优化（解决）此痛点，就必须将目标检测和识别移植到Android端，否则这个延时不可能会降下来。

### 2、解决方案

如题，采用 ncnn + yolov8 + opencv 三个框架实现这一目标

### 3、集成三个框架移植到Android端

#### 3.1、Android Studio 新建C++项目

注意，必须是C++项目，否则交叉编译环境会把人搞疯，不要走弯路了，步骤如下：
![微信截图_20240603170904.png](imags/微信截图_20240603170904.png)

![微信截图_20240603170934.png](imags/微信截图_20240603170934.png)

![微信截图_20240603170944.png](imags/微信截图_20240603170944.png)

图二根据自己熟悉的语言选择即可，不一定非得Kotlin(Java也是可以的)。至于其他的选项，建议按我的来，不然出问题了，会把人搞疯。

项目建完之后，按照Android Studio Giraffe版本的Android Studio会自动给你下载8.x版本的Gradle，高版本的Gradle的在Android
Studio
Giraffe上面不咋好使，咱改成本地的Gradle仓库路径（如果本地没有Gradle的，[点击下载](https://services.gradle.org/distributions/gradle-6.7.1-all.zip)
），如下图：

![微信截图_20240603171618.png](imags/微信截图_20240603171618.png)

然后修改项目根目录下的 [settings.gradle](settings.gradle) 如下：

```gradle
rootProject.name = "Test"
include ':app'
```

注意，rootProject.name修改为自己的项目名。其余代码全部删除，用不到。

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
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.casic.test">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application android:allowBackup="true" android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name" android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true" android:theme="@style/Theme.Test">
        <activity android:name=".MainActivity" android:exported="true">
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

```kotlin
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

#### 3.2、集成腾讯神经网络框架-ncnn

先下载[腾讯ncnn开源库](https://github.com/Tencent/ncnn)最新的框架，如图：

![微信截图_20240603180404.png](imags/微信截图_20240603180404.png)

然后解压，复制到项目的cpp目录下，**不要改任何文件以及代码**，如下图：
![微信截图_20240603180840.png](imags/微信截图_20240603180840.png)

修改新建项目时侯生成的 [CMakeLists.txt](app/src/main/cpp/CMakeLists.txt)，如下：

```cmake
project("test")

cmake_minimum_required(VERSION 3.22.1)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)
```

project根据实际情况修改即可，然后点击”Sync Now“

最后点击顶部三角形，如果能运行，就表示已经完成ncnn框架配置。

#### 3.3、集成opencv-mobile框架

同理，先去[opencv-mobile](https://objects.githubusercontent.com/github-production-release-asset-2e65be/327885181/315e5f06-4555-4466-83e7-a8efb5a8200c?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=releaseassetproduction/20240603/us-east-1/s3/aws4_request&X-Amz-Date=20240603T101806Z&X-Amz-Expires=300&X-Amz-Signature=35ce52016d06efbff0f8050f56fd126fdb4fa53d0c74e0d23f45be7e9e367bde&X-Amz-SignedHeaders=host&actor_id=20377551&key_id=0&repo_id=327885181&response-content-disposition=attachment%3B%20filename%3Dopencv-mobile-2.4.13.7-android.zip&response-content-type=application/octet-stream)
下载框架，如图：

![微信截图_20240603181800.png](imags/微信截图_20240603181800.png)

然后解压，复制到项目的cpp目录下，**不要改任何文件以及代码**，如下图：

![微信截图_20240603182024.png](imags/微信截图_20240603182024.png)

修改上一步的 [CMakeLists.txt](app/src/main/cpp/CMakeLists.txt)，添加如下代码：

```cmake
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-2.4.13.7-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)
```

在之前修改过的cmake文件里面加如上两行，然后点击”Sync Now“。最后点击顶部三角形，如果能运行，就表示已经完成opencv-mobile框架配置。

#### 3.4、集成OpenCV框架

这里的OpenCV和3.3里面的opencv-mobile是有区别的，opencv-mobile是专门针对移动端做了优化。此处引入OpenCV的目的是为了后面的画面预览的数据矩阵。同样去[OpenCV](https://objects.githubusercontent.com/github-production-release-asset-2e65be/5108051/eb6f2dc7-a522-4eec-92e5-264bf23fc9c1?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=releaseassetproduction/20240604/us-east-1/s3/aws4_request&X-Amz-Date=20240604T003102Z&X-Amz-Expires=300&X-Amz-Signature=72dae75af31861576e4e180beb484b496e82db395008de7082a583dd189917c1&X-Amz-SignedHeaders=host&actor_id=20377551&key_id=0&repo_id=5108051&response-content-disposition=attachment%3B%20filename%3Dopencv-4.9.0-android-sdk.zip&response-content-type=application/octet-stream)
官网下载最新的Android端SDK，如下图：

![微信截图_20240604083058.png](imags/微信截图_20240604083058.png)

然后解压在桌面备用，按如下步骤操作：

![QQ截图20240604083401.png](imags/QQ截图20240604083401.png)

![微信截图_20240604083549.png](imags/微信截图_20240604083549.png)

导入进去之后会报错，别慌，按下面步骤修改即可，如图：

首先修改app目录下面的[build.gradle](app/build.gradle)，在dependencies括号里面添加一行代码，如下图：

```gradle
implementation project(':sdk')
```

这里的”sdk“就是刚刚导入进来的OpenCV依赖库的名字，如果按照我的步骤来没有改过名字的应该就是这个，如果自己改过名字的，这里填写你改过的依赖库名字。

然后再修改sdk里面的[build.gradle](sdk/build.gradle)
，将里面的compileSdkVersion、minSdkVersion和targetSdkVersion三个字段改为了主项目一致，并删掉其中的如下代码：

```gradle
    publishing {
        singleVariant('release') {
            withSourcesJar()
            withJavadocJar()
        }
    }
```

最后在主界面点击”Try Again“即可完成OpenCV的集成，最后效果如下：

![微信截图_20240604084455.png](imags/微信截图_20240604084455.png)

到此，三大框架集成完毕。

### 4、导入自研yolov8的模型

在 app 的 main 目录下新建 assets 文件夹（一定要这个名字，别自己另辟蹊径），将Python导出的yolov8模型（后缀是 *.bin 和 *.param，如果不是这俩后缀的自行查找解决方案）复制进去即可。暂时先不用管，备用。

### 5、JNI编程

此模块需要有能看懂的C/C++代码的的能力，以及非常熟练的使用Kotlin/Java的能力。

#### 5.1、什么是JNI？

JNI（Java Native Interface），是方便Java/Kotlin调用C/C++等Native代码封装的一层接口。简单来说就是Java/Kotlin与C/C++沟通的桥梁。

#### 5.2、新建 [Yolov8ncnn.java](ap/src/main/java/com/pengxh/ncnn/yolov8/Yolov8ncnn.java)

在 **app/src/main/java/自己的包名** 目录下新建 Yolov8ncnn.java（Yolov8ncnn.kt 也是可以的），代码如下：

```java
public class Yolov8ncnn {
    static {
        System.loadLibrary("test");
    }

    public native boolean loadModel(AssetManager mgr, int model_id, int processor);

    public native boolean openCamera(int facing);

    public native boolean closeCamera();

    public native boolean setOutputWindow(Surface surface, DetectResult input, long nativeObjAddr, INativeCallback nativeCallback);
}
```

static（Kotlin里面是companion
object，伴生对象）包裹的代码意思是C/C++代码编译之后动态链接库的名字（此时还没有，因为还没有添加C/C++代码）。另外四个方法和普通的Java方法的区别在于全部都有native关键字修饰（Kotlin里面是external），表明这几个方法需要调用C/C++代码，也就是前文提到的”桥梁“。此时代码会报错，是因为还没有在C/C++里面实现它们，先不用管。

#### 5.3、将本项目的 [ndkcamera.cpp](app/src/main/cpp/ndkcamera.cpp) 和 [ndkcamera.h](app/src/main/cpp/ndkcamera.h) 复制到自己项目

这是底层相机相关的代码逻辑，包括相机打开、关闭、预览、数据回调等，通用代码，无需修改。此时会爆一堆错误提示，别慌，先不用管。

#### 5.4、将本项目的 [yolo.cpp](app/src/main/cpp/yolo.cpp) 和 [yolo.h](app/src/main/cpp/yolo.h) 复制到自己项目

这两代码文件主要的功能是对相机预览产生的nv21数据进行处理，包括nv21转换、nv21转Mat矩阵、图像裁剪、灰度处理、调用模型检测目标、显示检测结果、回调等一些列操作，底层逻辑就在此实现。此时依旧会爆一堆错误提示，别慌，先不用管。

#### 5.5、将本项目的 [yolov8ncnn.cpp](app/src/main/cpp/yolov8ncnn.cpp) 复制到自己项目

此代码文件主要包括相机初始化，参数初始化。整体来说就是各种初始化。

#### 5.6、修改 app/src/main/cpp 目录下的 [CMakeLists.txt](app/src/main/cpp/CMakeLists.txt)

添加如下两行代码：

```cmake
add_library(yolov8ncnn SHARED yolov8ncnn.cpp yolo.cpp ndkcamera.cpp)

target_link_libraries(yolov8ncnn ncnn ${OpenCV_LIBS} camera2ndk mediandk)
```

最终的cmake代码如下图：
![微信截图_20240604101534.png](imags/微信截图_20240604101534.png)

复制过去的文件，有四个函数一定是没有高亮的，如下图：

![微信截图_20240604101848.png](imags/微信截图_20240604101848.png)

此时需要将此函数根据情况修改为自己项目包名+函数名的方式，”.“用”_
“d代替，比如：com.casic.test.Yolov8ncnn应改为Java_com_casic_test_Yolov8ncnn，改为之后就会发现，这四个函数已经高亮了，说明桥接代码已经生效。

* Java
  ![微信截图_20240604093515.png](imags/微信截图_20240604093515.png)

* Cpp
  ![微信截图_20240604101047.png](imags/微信截图_20240604101047.png)

可以看到Java和C++代码左侧已经出现相对应的代码标识。另外还有两个文件，就是setOutputWindow方法里面的DetectResult和INativeCallback，这俩都属于回调部分的代码，一个是数据模型类，一个是回调接口，直接复制即可。

如果没有出现以上效果的，先点击”Sync Now“，再”Build-Clean Project“，再”Build-Rebuild
Project“，再”Build-Refresh Linked C++ Projects“，最后关闭工程重新启动Android Studio，此时应该就没问题了。

////////////////////////////未完待续////////////////////////////

最后感谢各个开源者的辛勤付出（排名不分先后）：

* [ncnn](https://github.com/Tencent/ncnn)
  ：腾讯开源的一个为手机端极致优化的高性能神经网络前向计算框架，无第三方依赖，跨平台，ncnn主要基于C++和caffe。
* [yolov8](https://github.com/ultralytics/ultralytics): YOLO(You Only Look
  Once）是一种流行的物体检测和图像分割模型，由华盛顿大学的约瑟夫-雷德蒙（Joseph Redmon）和阿里-法哈迪（Ali
  Farhadi）开发，YOLO 于 2015 年推出。
* [opencv-mobile](https://github.com/nihui/opencv-mobile): The minimal opencv for Android, iOS, ARM
  Linux, Windows, Linux, MacOS, WebAssembly
* [OpenCV](https://github.com/opencv/opencv): OpenCV（open source computer vision
  library）是一个基于BSD许可（开源）发行的跨平台计算机视觉库，可以运行在Linux、Windows、Android和Mac
  OS操作系统上。