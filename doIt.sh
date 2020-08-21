if [ -z $1 ]
then
	URL="https://manamoa53.net/bbs/page.php?hid=manga_detail&manga_id=4285"
else
	URL="$1"
fi

echo "TARGET URL: $URL"

gradle run -q --args="$URL"

if [ -d "comics" ]
then
	tree ./comics/
fi

