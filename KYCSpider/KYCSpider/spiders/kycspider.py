# -*- coding: utf-8 -*-
import scrapy

class KYCSpider(scrapy.Spider):
    name = 'kycspider'
    start_urls = [
        'http://www.vlada.si/en/about_the_government/members_of_government/'
        ]
    allowed_domains = ['www.vlada.si']
    maxdepth = 1
    def parse(self, response):
        from_designation = ''
        depth = 0;
        if 'name' in response.meta: from_name = str(response.meta['name'])
        if 'depth' in response.meta: depth = response.meta['depth']
        if 'designation' in response.meta: from_designation = str(response.meta['designation'])
        if depth == self.maxdepth:
            sourceURL = str(response.url)
            c3right = response.css('div.c3.right')
            e = c3right.xpath('.//a[contains(@href, "javascript")]/text()').getall()
            #Sometimes the a tag is split up into 3 lines instead of 1 line
            if len(e) == 1:
                email = e[0]
            elif len(e) == 3:
                email = e[0] + '@' + e[1] + '.' + e[2]
            
            f = c3right.xpath('.//p/text()').extract()
            if f[0] == '\r':
                address = f[1]
                phone = f[2]
            else:
                address = f[0]
                phone = f[1]
            
            phone = phone[phone.find("+"):].rstrip("\n\r") 
            address = address.rstrip("\n\r") 
            website = c3right.xpath('.//a[contains(@href, "http")]/text()').get()
            
            c9main = response.css('div.c9.main')
            g = c9main.xpath('.//span/text()').extract()
            dob = ""
            months = ["january", "february","march","april","may","june","july",
                      "august","september","october","november","december"]
            for elem in g:
                if "born" in elem.lower():
                    birth = elem.split(" ")
                    for s in birth:
                        string = s.strip()
                        if string.isdigit(): #year or date
                            if(len(string) == 4): #if year
                                year = string
                            elif len(string) == 1: #single digit date
                                day = "0" + string
                            else:
                                day = string
                        elif string.lower() in months: #is a month
                            month = str(months.index(string.lower()) + 1)
                            if len(month) == 1:
                                month = "0" + month
                    dob = month + "/" + day + "/" + year

            #with open('data.json', 'a', encoding='utf8') as json_file:
            yield { 
                    'name': from_name, 
                    'designation': from_designation,
                    'dob': dob,
                    'address': address,
                    'email': email,
                    'phone': phone,
                    'website': website,
                    'sourceURL': sourceURL
                    }
            
         
        elif depth < self.maxdepth:
            div_selectors = response.css("div.c4.bp2")
            for selector in div_selectors:
                designation = selector.css("h6").xpath("text()").extract_first()
                a_selector = selector.css("a.internal-link")
                link = a_selector.xpath("@href").extract_first()
                name = a_selector.xpath("text()").extract_first()
                request = response.follow(link, callback=self.parse)
                request.meta['from'] = response.url
                request.meta['designation'] = designation
                request.meta['name'] = name
                request.meta['depth'] = depth + 1
                yield request
