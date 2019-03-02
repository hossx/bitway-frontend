cd /var/bitway/code/bitway-frontend
git fetch
releaseBranch=`git branch | grep $1`
if [ -z "$releaseBranch" ];then
  git checkout -b $1 origin/$1
else
  git checkout $1
fi
./activator clean dist
rm -rf /var/bitway/frontend/bitway-frontend-*
cp target/universal/bitway-frontend-* /var/bitway/frontend
cd /var/bitway/frontend
unzip bitway-frontend-*
