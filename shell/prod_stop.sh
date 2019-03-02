ps -ef | grep 'bitway-frontend' | grep -v 'grep' | awk '{print $2}' | xargs kill
