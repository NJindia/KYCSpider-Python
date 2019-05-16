# KYCSpider
Creates a webcrawler that crawls through http://www.vlada.si/en/about_the_government/members_of_government/ and writes each government member's name, designation, date of birth, address, email, phone, website, and the sourceURL to [data.json](KYCSpider/data.json). If data.json does not exist yet, it is created.

In addition, each member is assigned an operation, which is determined mainly by looking at the sourceURL. If data.json did not exist, all members' operation is set to "New". If the sourceURL does not exist in the existing data.json file, that member's operation is set to "New". If the sourceURL exists but the data (name, designation, dob, address, email, phone, or website) has changed, that member's operation is set to "Changed". If a member's sourceURL exists in the existing data.json file but does not exist in the new scraped data, that member's operation is set to "Deleted". Otherwise, if nothing has changed, the member's operation is set to "Unmodified".

Run by doing `scrapy crawl kycspider` in the same directory as scrapy.cfg
