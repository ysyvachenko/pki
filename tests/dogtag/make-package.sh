#!/bin/sh

### Exit if there not enough parameters specified.
if [ $# -lt 2 ];
then
  echo "Usage: ./make-package.sh User-ID Job_xml_config_file [Different_subfolder_for_each_beaker_job_?(Y/N)]"
  exit -1
fi

### Store the actual Makefile checked in SVN
mv Makefile .Makefile.save

user_id=$1
date_time="`date -u +%Y%m%d%H%M%S`"
rpm_identifier=".$date_time"
if [ $# -gt 1 ];
then
    if [ $2 = 'Y'  -o  $2 = 'y' ];
    then
        user_id="$1/$date_time"
        rpm_identifier=""
    fi
fi

### Replacing the default value with the "user_id/current_number"
sed -e "s|PKI_TEST_USER_ID|${user_id}|g" -e "s|_RPM_IDENTIFIER|${rpm_identifier}|g" .Makefile.save >> Makefile

chmod +x Makefile

### Making the rpm
make package

### Remove the current makefile and place the original back

rm -rf Makefile

mv .Makefile.save Makefile

sed -e "s|PKI_TEST_USER_ID|${user_id}|g"  beakerjob.rhcs.xml.template >> beakerjob.rhcs.xml

python update_beaker_job.py beakerjob.rhcs.xml $2
