package io.github.sdwfqin;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.ogr;
import org.locationtech.proj4j.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * <p>
 * 使用Gdal库进行转换
 * </p>
 *
 * @author zhangqin
 * @since 2024/2/23
 */
public class GdalMain {

    private static final String PROJ4_FROM = "+proj=tmerc +lat_0=0 +lon_0=114 +k=1 +x_0=3800000 +y_0=0 +ellps=krass +units=m +no_defs";
    private static final String PROJ4_TO = "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs";
    private static final boolean PROJ4_ENABLE = true;

    private static final Log log = LogFactory.get();

    public static void main(String[] args) {

        coordinateTransform();

        // gdal注册所有的驱动
        ogr.RegisterAll();

//        cadToGeoJson("opt/test.dwg", "opt/dwg.geojson");
        String absolutePath = new File("opt/test.dxf").getAbsolutePath();
        cadToGeoJson("absolutePath", "opt/dxf.geojson");
    }

    /**
     * cad/dxf转换geojson
     */
    public static void cadToGeoJson(String cadFile, String geoJsonFile) {
        gdal.SetConfigOption("DXF_FEATURE_LIMIT_PER_BLOCK", "-1");
        gdal.SetConfigOption("DXF_ENCODING", "UTF-8");
        //打开数据
        DataSource ds = ogr.Open(cadFile, 0);
        if (ds == null) {
            log.error("打开文件" + cadFile + "失败！");
            return;
        }
        log.info("打开文件成功！");

        // 创建输出数据源
        org.gdal.ogr.Driver dv = ogr.GetDriverByName("GeoJSON");
        if (dv == null) {
            log.error("打开驱动失败！");
            return;
        }
        log.info("打开驱动成功！");
        //输出geojson的位置及文件名
        DataSource dataSource = dv.CopyDataSource(ds, geoJsonFile);
        dataSource.FlushCache();
        dataSource.Close();
        log.info("转换成功！");

        if (PROJ4_ENABLE) {
            geoJsonTransform(geoJsonFile);
        }
    }

    /**
     * geojson 坐标转换
     *
     * @param geoJsonFileName geojson文件路径
     */
    public static void geoJsonTransform(String geoJsonFileName) {
        String userDir = System.getProperty("user.dir");

        Charset charset = StandardCharsets.UTF_8;

        try {
            File file = new File(geoJsonFileName);
            InputStream in = Files.newInputStream(file.toPath());
            byte[] b = new byte[3];
            in.read(b);
            in.close();
            if (!(b[0] == -17 && b[1] == -69 && b[2] == -65)) {
                charset = Charset.forName("GBK");
            }
        } catch (IOException ignored) {

        }


        FileReader fileReader = new FileReader(userDir + "\\" + geoJsonFileName);
        fileReader.setCharset(charset);
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
            forGeoJsonNode(coordinates, transform);
        }
        FileWriter writer = new FileWriter(userDir + File.separator + geoJsonFileName);
        writer.write(rootJsonObject.toString());
        log.info("坐标转换成功！");
    }

    public static void forGeoJsonNode(JSONArray jsonArray, CoordinateTransform transform) {
        for (int i = 0; i < jsonArray.size(); i++) {
            if (jsonArray.get(i) instanceof JSONArray) {
                forGeoJsonNode((JSONArray) jsonArray.get(i), transform);
            } else {
                transformGeoJsonNode(jsonArray, transform);
                break;
            }
        }

    }

    public static void transformGeoJsonNode(JSONArray jsonArray, CoordinateTransform transform) {
        Double x = jsonArray.getDouble(0);
        if (jsonArray.getDouble(0).intValue() >= 10000000) {
            x = x - 38000000;
        }
        Double z = 0.0;
        try {
            z = jsonArray.getDouble(2);
        } catch (Exception ignored) {

        }
        ProjCoordinate sourceCoordinate = new ProjCoordinate(x, jsonArray.getDouble(1), z);
        ProjCoordinate targetCoordinate = new ProjCoordinate();
        transform.transform(sourceCoordinate, targetCoordinate);

        if (Double.isNaN(targetCoordinate.z)) {
            targetCoordinate.z = sourceCoordinate.z;
        }

        jsonArray.set(0, targetCoordinate.x);
        jsonArray.set(1, targetCoordinate.y);
        jsonArray.set(2, targetCoordinate.z);
    }

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
}
