package univ.bupt.soon.failure;


import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.soon.dataset.DatabaseAdapter;
import org.onosproject.soon.dataset.original.FailureClassificationItem;
import org.onosproject.soon.dataset.original.Item;
import org.onosproject.soon.foreground.MLAppRegistry;
import org.onosproject.soon.foreground.MLAppType;
import org.onosproject.soon.platform.MLPlatformService;
import org.postgresql.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import univ.bupt.soon.failure.classification.FailClsPlatformCallback;
import univ.bupt.soon.failure.classification.FailureClassification;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Properties;

/**
 * 故障处理相关的app。目前看来包括故障预测和故障分类
 */
@Component
public class FailureComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MLAppRegistry mlAppRegistry;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MLPlatformService platformService;

    private ApplicationId appId;
    private DatabaseAdapter database = new InternalDatabaseAdapter();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("unive.bupt.soon.almpred");

        // 构建故障分类服务
        FailureClassification failureClassification = new FailureClassification(MLAppType.FAILURE_CLASSIFICATION,
                "failure_class", FailureClassificationItem.class,
                FailClsPlatformCallback.class, database, platformService);

        // 注册服务
        mlAppRegistry.register(failureClassification, failureClassification.getServiceName());
        database.connect();
    }

    @Deactivate
    protected void deactivate() {
        mlAppRegistry.unregister(MLAppType.FAILURE_CLASSIFICATION);
        database.close();
        log.info("SOON - failure - Stopped");
    }





    /**
     * 数据库操作的相关内部类
     */
    class InternalDatabaseAdapter extends DatabaseAdapter {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private Connection conn = null;
        private Statement stmt = null;
        private final String JDBC_DRIVER;
        private final String DB_URL;
        private final String USER;
        private final String PASS;
        private final String DB_NAME = "ecoc2018";

        public InternalDatabaseAdapter() {
            String path = System.getenv("ONOS_ROOT");
            String file = path + "/soon/resources/database.properties";
            java.util.Properties properties = new Properties();
            try {
                properties.load(new FileInputStream(file));
            } catch (IOException e) {
                e.printStackTrace();

            }
            JDBC_DRIVER = properties.getProperty("JDBC_DRIVER");
            DB_URL = properties.getProperty("DB_URL");
            USER = properties.getProperty("USER");
            PASS = properties.getProperty("PASS");

        }

        /**
         * 连接数据库
         *
         * @return 是否连接成功
         */
        @Override
        public boolean connect() {
            Driver driver = new Driver();
            try {
                if (conn == null || conn.isClosed()) {
                    // 如果连接已经关闭，则重启连接
                    Class.forName(JDBC_DRIVER);
                    conn = DriverManager.getConnection(DB_URL + DB_NAME, USER, PASS);
                    stmt = conn.createStatement();
                    return true;
                } else {
                    // 如果连接仍然开启，则直接返回
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        /**
         * 单表查询，不支持联合查询
         *
         * @param items      select之后的部分
         * @param constraint 查询的约束语句，即在where之后的部分
         * @param tableName  表名
         * @param cls        返回Item的实现类名
         * @return 返回查询结果
         */
        @Override
        public List<Item> queryData(List<String> items, String constraint, String tableName, Class cls) {
            StringBuilder builder = new StringBuilder("SELECT ");
            for (String item : items) {
                builder.append(item).append(',');
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.append(" FROM ").append(tableName).append(" ").append(constraint);
            String query = builder.toString();
            log.info(query);
            // 开始查询ResultSet
            try {
                ResultSet rs = stmt.executeQuery(query);
                return parseResultSet(rs, cls);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public List<Item> queryData(String s, Class cls) {
            try {
                ResultSet rs = stmt.executeQuery(s);
                return parseResultSet(rs, cls);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * 关闭数据库连接
         */
        @Override
        public void close() {
            try {
                stmt.close();
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
