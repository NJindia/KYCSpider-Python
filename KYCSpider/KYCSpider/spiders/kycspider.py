# -*- coding: utf-8 -*-
import scrapy
from scrapy import signals
from scrapy.xlib.pydispatch import dispatcher
import json

class KYCSpider(scrapy.Spider):
    name = 'kycspider'
    start_urls = [
        'http://www.vlada.si/en/about_the_government/members_of_government/'
        ]
    allowed_domains = ['www.vlada.si']
    maxdepth = 1
    isNewDoc = False
    newURLs = []
    oldURLs = []
    oldData = ''
    data = {}
    data['Government Members'] = []
    
    def __init__(self):
        dispatcher.connect(self.spider_opened, signals.spider_opened)
        dispatcher.connect(self.spider_closed, signals.spider_closed)
    
    def spider_opened(self, spider):
        try:
            open('data.json', 'r')
            with open('data.json', encoding = 'utf-8-sig') as json_file:  
                self.oldData = json.load(json_file)
                for p in self.oldData['Government Members']:
                    self.oldURLs.append(p['sourceURL'])
            self.isNewDoc = False
        #If data.json file doesn't exist, tell spider that this is a new doc
        except FileNotFoundError:
            self.isNewDoc = True      
        
    def spider_closed(self, spider):
        if self.isNewDoc == False: 
            self.check_deleted()  #Not necessary if it's a new doc
        with open('data.json', 'w', encoding='utf8') as outfile:
            json.dump(self.data, outfile, ensure_ascii=False, indent = 4)            
    
    def check_deleted(self):
        for p in self.oldData['Government Members']:
            if p['sourceURL'] not in self.newURLs:
                self.data['Government Members'].append({
                    'name': p['name'],
                    'designation': p['designation'],
                    'dob': p['dob'],
                    'address': p['address'],
                    'email': p['email'],
                    'phone': p['phone'],
                    'website': p['website'],
                    'sourceURL': p['sourceURL'],
                    'operation': 'Deleted'
                })
    
    
    def parse(self, response):
        designation = ''
        depth = 0;
        if 'name' in response.meta: from_name = str(response.meta['name'])
        if 'depth' in response.meta: depth = response.meta['depth']
        if 'designation' in response.meta: designation = str(response.meta['designation'])
        if depth == self.maxdepth:
            sourceURL = str(response.url)
            self.newURLs.append(sourceURL)
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
            
            phone = phone[phone.find('T:') + 2:].lstrip(' ').rstrip("\n\r")
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
            
            operation = 'Unmodified'
            if self.isNewDoc == True:
                operation = 'New'
            else:
                found = False
                for p in self.oldData['Government Members']:
                    if sourceURL == p['sourceURL']:
                        found = True
                        #If any data is changed, make operation "Changed"
                        if (from_name != p['name'] or designation != p['designation'] or 
                            dob != p['dob'] or address != p['address'] or email != p['email'] or 
                            phone != p['phone'] or website != p['website']):
                            operation = 'Changed'
                if found == False:
                    operation = 'New' #If new sourceURL is introduced
                
            self.data['Government Members'].append({
                    'name': from_name, 
                    'designation': designation,
                    'dob': dob,
                    'address': address,
                    'email': email,
                    'phone': phone,
                    'website': website,
                    'sourceURL': sourceURL,
                    'operation': operation
            })
            
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
