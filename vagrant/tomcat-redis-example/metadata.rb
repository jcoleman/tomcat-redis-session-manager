name             'tomcat-redis-example'
maintainer       'James Coleman'
maintainer_email 'jtc331@gmail.com'
license          'MIT'
description      'Installs/Configures tomcat-redis-example'
long_description 'Installs/Configures tomcat-redis-example'
version          '0.1.0'

depends 'apt', '~> 2.4'
depends 'tomcat', '~> 0.16.2'
depends 'redisio', '~> 2.2.3'

%w{ ubuntu debian }.each do |os|
  supports os
end