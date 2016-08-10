WEB_INF = ../../../../WEB-INF
JDK_PROPS = -Duser.home=$(shell (cd $(WEB_INF); pwd))

LIB_DIR = $(WEB_INF)/lib
CLASS_DIR = $(WEB_INF)/classes
DEPLOY_DIR = $(CLASS_DIR)/org/libx/libappdatabase
CLASSPATH = \
	$(LIB_DIR)/basex-6.2.7.jar \
	$(LIB_DIR)/basex-api-6.2.7.jar \
	$(LIB_DIR)/xqj-api-1.0.jar \
	$(LIB_DIR)/zk.jar \
	$(LIB_DIR)/zkplus.jar \
	$(LIB_DIR)/zul.jar \
	$(LIB_DIR)/zcommon.jar \
	$(LIB_DIR)/junit-4.8.1.jar \
	$(LIB_DIR)/servlet-api.jar \

VOID =
SPACE = $(VOID) $(VOID)
JFLAGS = -cp $(subst $(SPACE),:,$(CLASSPATH))
#JFLAGS = -Xlint:unchecked -cp $(subst $(SPACE),:,$(CLASSPATH))
JCC = javac
JRE = java

SOURCES = *.java
CLASSES = *.class
QUERIES = queries/

default: 
	test -d $(DEPLOY_DIR) || mkdir -p $(DEPLOY_DIR)
	@($(JCC) $(JFLAGS) $(SOURCES) ; (mv $(CLASSES) $(DEPLOY_DIR)))

clean: 
	@($(RM) $(CLASSES) ; $(RM) $(DEPLOY_DIR)/$(CLASSES))

publish:
	@($(JRE) $(JFLAGS):$(CLASS_DIR) org.libx.libappdatabase.Publisher $(uri))

test:
	@($(JRE) $(JFLAGS):$(CLASS_DIR) org.libx.libappdatabase.Test)
	
allfeeds:
	sed 's,<id></id>,<id>$(base_uri)</id>,g' base_feed.xml > libx2_work;
	cp feed_backup.xml libx2_feed
	cp meta_backup.xml libx2_metadata

install:
	java $(JDK_PROPS) $(JFLAGS) org.basex.BaseX -vc "\
		create db libx2_feed libx2_feed;\
		create db libx2_work libx2_work;\
		create db libx2_meta libx2_metadata\
	";
	echo "\nCopying files..." ;
	chmod -R g+rwx $(WEB_INF)/BaseXData/ ;
	chgrp -R libx $(WEB_INF)/BaseXData/ ;
	rm libx2_feed libx2_metadata libx2_work
