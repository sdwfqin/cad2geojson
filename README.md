# cad、dxf转geojson

> 建议优先使用dxf，cad可以转但是可能会有问题

# 配置dgal环境
## windows
### 下载gdal
Windows环境下，可以下载编译好的gdal文件
#### 下载地址
[http://www.gisinternals.com/archive.php](http://www.gisinternals.com/archive.php)
![image.png](https://cdn.nlark.com/yuque/0/2024/png/177396/1708668491634-7ba023b4-e507-45cb-9eb5-1eb2be13d220.png#averageHue=%23eae7e6&clientId=u130ba411-f936-4&from=paste&height=643&id=yJwaW&originHeight=1125&originWidth=2876&originalType=binary&ratio=1.75&rotation=0&showTitle=false&size=462598&status=done&style=none&taskId=uf50f199d-1fbb-4eb2-ba2c-1a66ea18ef6&title=&width=1643.4285714285713)
#### env配置
所有的dll放到一个目录（跟目录+java目录合到一起，也可以直接用demo的gdal目录），然后把路径设置到环境变量中的path
![image.png](https://cdn.nlark.com/yuque/0/2024/png/177396/1709013632752-f563f8f7-59e1-4d03-8e69-63682291d38d.png#averageHue=%23ebeae9&clientId=ud87b50b7-abf9-4&from=paste&height=617&id=u34926850&originHeight=1079&originWidth=1139&originalType=binary&ratio=1.75&rotation=0&showTitle=false&size=119885&status=done&style=none&taskId=u20207f2d-a2cd-4d44-97f5-bc602725ebc&title=&width=650.8571428571429)
## linux
### 下载gdal文件
[https://gdal.org/download.html](https://gdal.org/download.html)
### 编译文件
[https://gdal.org/development/building_from_source.html](https://gdal.org/development/building_from_source.html)
#### **Ubuntu 22.04**
> cmake 版本 3.9...3.27！！！

```shell
sudo apt install cmake
sudo apt install g++
sudo apt-get install libproj-dev proj-data proj-bin
sudo apt-get install swig
sudo apt-get install ant
tar -zxvf gdal-3.8.4.tar.gz
cd gdal-3.8.4/
mkdir build
cd build
cmake ..

// 检查BUILD_JAVA_BINDINGS配置
vi CMakeCache.txt
// 修改BUILD_JAVA_BINDINGS为ON
BUILD_JAVA_BINDINGS:BOOL=ON

cmake --build .
sudo cmake --build . --target install

sudo vi /etc/profile

// 指定库gdal文件路径
// 我这里将编译出来的文件/home/zhangqin/gdal/gdal-3.8.4/build/swig/java复制到了/opt/gdal/java
export LD_LIBRARY_PATH=/opt/gdal/java:$LD_LIBRARY_PATH

source /etc/profile
```

#### **CentOS 7**
##### sqlite3
```shell
wget https://www.sqlite.org/2019/sqlite-autoconf-3290000.tar.gz
tar -zxvf sqlite-autoconf-3290000.tar.gz
cd sqlite-autoconf-3290000
make && make install
mv /usr/bin/sqlite3  /usr/bin/sqlite3_old
ln -s /usr/local/bin/sqlite3   /usr/bin/sqlite3
echo "/usr/local/lib" > /etc/ld.so.conf.d/sqlite3.conf
ldconfig
sqlite3 -version
```
##### proj-8.2.1
[https://github.com/OSGeo/PROJ/releases/tag/8.2.1](https://github.com/OSGeo/PROJ/releases/tag/8.2.1)
```shell
tar -zxvf proj-8.2.1.tar.gz
cd proj-8.2.1
export PKG_CONFIG_PATH=$PKG_CONFIG_PATH:/usr/local/lib/pkgconfig
yum install libcurl-devel
./configure
make
make install
```
##### g++11
```shell
yum install centos-release-scl
yum install devtoolset-11-gcc devtoolset-11-gcc-c++
source /opt/rh/devtoolset-11/enable
```
##### gdal
> 上面两个包安装完之后参考ubuntu
```shell
vi /etc/profile
export LD_LIBRARY_PATH=/usr/local/lib64:/opt/gdal/java:$LD_LIBRARY_PATH
source /etc/profile
```

# cad、dxf转geojson
## pom
```xml
<dependency>
  <groupId>org.gdal</groupId>
  <artifactId>gdal</artifactId>
  <version>3.8.0</version>
</dependency>
```
## java代码
```java
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.ogr;

/**
 * cad/dxf转换geojson
 */
public static void cadToGeoJson(String cadFile, String geoJsonFile) {
    // gdal注册所有的驱动
    ogr.RegisterAll();
    // 为了支持中文路径，请添加下面这句代码
    gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", "YES");
    // 为了使属性表字段支持中文，请添加下面这句
    gdal.SetConfigOption("SHAPE_ENCODING", "");
    // 设置DXF缺省编码
    gdal.SetConfigOption("DXF_ENCODING", "ASCII");
    // 打开数据
    DataSource ds = ogr.Open(cadFile, 0);
    if (ds == null) {
        log.error("打开文件" + cadFile + "失败！");
        return;
    }
    log.info("打开文件成功！");
    org.gdal.ogr.Driver dv = ogr.GetDriverByName("GeoJSON");
    if (dv == null) {
        log.error("打开驱动失败！");
        return;
    }
    log.info("打开驱动成功！");
    // 输出geojson的位置及文件名
    DataSource dataSource = dv.CopyDataSource(ds, geoJsonFile);
    dataSource.FlushCache();
    log.info("转换成功！");

    if (PROJ4_ENABLE) {
        geoJsonTransform(geoJsonFile);
    }
}

```
# 坐标转换
> 以EPSG:2414转WGS84为例

## pom
```xml
<dependency>
    <groupId>org.locationtech.proj4j</groupId>
    <artifactId>proj4j</artifactId>
    <version>1.3.0</version>
</dependency>
```
## java代码
```java
private static final String PROJ4_FROM = "+proj=tmerc +lat_0=0 +lon_0=114 +k=1 +x_0=3800000 +y_0=0 +ellps=krass +units=m +no_defs";
private static final String PROJ4_TO = "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs";

public static void coordinateTransform() {
    // 创建坐标转换器
    CRSFactory factory = new CRSFactory();

    // 解析CGCS2000坐标系
    CoordinateReferenceSystem sourceCRS = factory.createFromParameters("PROJ4_FROM", PROJ4_FROM);

    // 解析WGS84坐标系
    CoordinateReferenceSystem targetCRS = factory.createFromParameters("PROJ4_TO", PROJ4_TO);

    CoordinateTransform transform = new CoordinateTransformFactory().createTransform(sourceCRS, targetCRS);

    ProjCoordinate sourceCoordinate = new ProjCoordinate(551751.13439085, 4076430.085220524, 0);
    ProjCoordinate targetCoordinate = new ProjCoordinate();
    transform.transform(sourceCoordinate, targetCoordinate);

    if (Double.isNaN(targetCoordinate.z)) {
        targetCoordinate.z = sourceCoordinate.z;
    }

    // 输出WGS84坐标结果
    log.info("sourceCoordinate: {}", sourceCoordinate);
    log.info("targetCoordinate: {}", targetCoordinate);
}
```
# geojson坐标转换
```java
/**
 * geojson 坐标转换
 * 
 * @param geoJsonFileName geojson文件路径
 */
public static void geoJsonTransform(String geoJsonFileName) {
    String userDir = System.getProperty("user.dir");


    FileReader fileReader = new FileReader(userDir + "\\" + geoJsonFileName);
    String result = fileReader.readString();

    // 创建坐标转换器
    CRSFactory factory = new CRSFactory();
    // 解析CGCS2000坐标系
    CoordinateReferenceSystem sourceCRS = factory.createFromParameters("PROJ4_FROM", PROJ4_FROM);
    // 解析WGS84坐标系
    CoordinateReferenceSystem targetCRS = factory.createFromParameters("PROJ4_TO", PROJ4_TO);
    CoordinateTransform transform = new CoordinateTransformFactory().createTransform(sourceCRS, targetCRS);

    JSONObject rootJsonObject = JSON.parseObject(result);
    JSONArray features = rootJsonObject.getJSONArray("features");
    for (int i = 0; i < features.size(); i++) {
        JSONObject item = features.getJSONObject(i);
        JSONObject geometry = item.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        if (geometry.getString("type").equals("Point")){
            transformGeoJsonNode(coordinates, transform);
        } else {
            for (int i1 = 0; i1 < coordinates.size(); i1++) {
                transformGeoJsonNode(coordinates.getJSONArray(i1), transform);
            }
        }
    }
    FileWriter writer = new FileWriter(userDir + "/opt/bbb.geojson");
    writer.write(rootJsonObject.toString());
    log.info("坐标转换成功！");
}

public static void transformGeoJsonNode(JSONArray jsonArray, CoordinateTransform transform) {
    Double x = jsonArray.getDouble(0);
    if (jsonArray.getDouble(0).intValue() >= 10000000) {
        x = x - 38000000;
    }
    ProjCoordinate sourceCoordinate = new ProjCoordinate(x, jsonArray.getDouble(1), jsonArray.getDouble(2));
    ProjCoordinate targetCoordinate = new ProjCoordinate();
    transform.transform(sourceCoordinate, targetCoordinate);

    if (Double.isNaN(targetCoordinate.z)) {
        targetCoordinate.z = sourceCoordinate.z;
    }

    jsonArray.set(0, targetCoordinate.x);
    jsonArray.set(1, targetCoordinate.y);
    jsonArray.set(2, targetCoordinate.z);
}
```
