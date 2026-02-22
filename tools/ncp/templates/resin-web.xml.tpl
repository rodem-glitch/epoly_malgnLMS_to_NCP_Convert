<web-app xmlns="http://caucho.com/ns/resin">
  <!-- 왜: CI 배포 시 WEB-INF/classes가 비어 있어도 src를 기준으로 필요한 클래스를 즉시 컴파일해
       dao.* 누락으로 첫 화면이 500 나는 장애를 막습니다. -->
  <class-loader>
    <compiling-loader path="WEB-INF/classes" source="__LEGACY_SOURCE_DIR__"/>
    <library-loader path="WEB-INF/lib"/>
  </class-loader>

  <!-- 왜: 레거시 코드가 Java 8 문법 기준이라 컴파일 타깃을 고정합니다. -->
  <javac compiler="internal" args="-source 1.8 -target 1.8 -encoding UTF-8 -Xlint:unchecked -Xlint:-options"/>

  <!-- 왜: 레거시 공통 라이브러리(Config.getJndi)가 jdbc/malgn을 기본 사용합니다.
       NCP Cloud DB 엔드포인트(__NCP_DB_HOST__)를 사용합니다. -->
  <database>
    <jndi-name>jdbc/malgn</jndi-name>
    <driver type="com.mysql.cj.jdbc.Driver">
      <url>__APP_DB_URL__</url>
      <user>__DB_USER__</user>
      <password>__DB_PASSWORD__</password>
    </driver>
    <prepared-statement-cache-size>8</prepared-statement-cache-size>
    <max-connections>16</max-connections>
    <max-idle-time>30s</max-idle-time>
  </database>

  <!-- 왜: 일부 화면은 jdbc/lms를 직접 참조하므로 같은 연결을 함께 제공합니다. -->
  <database>
    <jndi-name>jdbc/lms</jndi-name>
    <driver type="com.mysql.cj.jdbc.Driver">
      <url>__APP_DB_URL__</url>
      <user>__DB_USER__</user>
      <password>__DB_PASSWORD__</password>
    </driver>
    <prepared-statement-cache-size>8</prepared-statement-cache-size>
    <max-connections>16</max-connections>
    <max-idle-time>30s</max-idle-time>
  </database>
</web-app>
