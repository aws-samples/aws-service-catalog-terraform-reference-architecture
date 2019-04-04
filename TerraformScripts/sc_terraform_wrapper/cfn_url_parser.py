from datetime import datetime, timedelta, timezone
from urllib.parse import parse_qs
import dateutil.parser

# Reserved interval in seconds to post response before response url timeout
RESERVED_RESPONSE_INTERVAL = 60

def seconds_until_expiry(response_url):
    qs = parse_qs(response_url)

    if 'Expires' in qs:
        expire_time = qs['Expires'][0]
        current_time = datetime.now(timezone.utc).timestamp()
        return int(expire_time) - int(current_time) - RESERVED_RESPONSE_INTERVAL

    elif 'X-Amz-Expires' in qs and 'X-Amz-Date' in qs:
        amz_date_str = qs['X-Amz-Date'][0]
        amz_date = dateutil.parser.parse(amz_date_str)
        current_date = datetime.now(amz_date.tzinfo)

        url_duration_seconds = int(qs['X-Amz-Expires'][0])
        url_duration = timedelta(seconds=url_duration_seconds)
        seconds_until_expiry = (amz_date + url_duration - current_date).total_seconds()
        return int(seconds_until_expiry) - RESERVED_RESPONSE_INTERVAL

    else:
        raise Exception('Unexpected CloudFormation Response URL format')
