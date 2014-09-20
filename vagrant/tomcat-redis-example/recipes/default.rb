node.set['java']['jdk_version'] = '7'

node.set["tomcat"]["base_version"] = 7
node.set['tomcat']['user'] = "tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['group'] = "tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['home'] = "/usr/share/tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['base'] = "/var/lib/tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['config_dir'] = "/etc/tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['log_dir'] = "/var/log/tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['tmp_dir'] = "/tmp/tomcat#{node["tomcat"]["base_version"]}-tmp"
node.set['tomcat']['work_dir'] = "/var/cache/tomcat#{node["tomcat"]["base_version"]}"
node.set['tomcat']['context_dir'] = "#{node["tomcat"]["config_dir"]}/Catalina/localhost"
node.set['tomcat']['webapp_dir'] = "/var/lib/tomcat#{node["tomcat"]["base_version"]}/webapps"
node.set['tomcat']['lib_dir'] = "#{node["tomcat"]["home"]}/lib"
node.set['tomcat']['endorsed_dir'] = "#{node["tomcat"]["lib_dir"]}/endorsed"

#Chef::Log.info "node: #{node.to_hash.inspect}"

include_recipe 'redisio'
include_recipe 'redisio::enable'

include_recipe 'tomcat'

lib_dir = File.join(node['tomcat']['base'], 'lib')

directory(lib_dir) do
  user node['tomcat']['user']
  group node['tomcat']['group']
end

remote_file(File.join(lib_dir, 'spark-core-1.1.1.jar')) do
  source 'http://central.maven.org/maven2/com/sparkjava/spark-core/1.1.1/spark-core-1.1.1.jar'
  action :create_if_missing
end

remote_file(File.join(lib_dir, 'commons-pool2-2.2.jar')) do
  source 'http://central.maven.org/maven2/org/apache/commons/commons-pool2/2.2/commons-pool2-2.2.jar'
  action :create_if_missing
end

remote_file(File.join(lib_dir, 'jedis-2.5.2.jar')) do
  source 'http://central.maven.org/maven2/redis/clients/jedis/2.5.2/jedis-2.5.2.jar'
  action :create_if_missing
end
