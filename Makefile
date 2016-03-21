JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
		  Server.java \
		  ServerThread.java \
		  Client.java \
		  GarbageCollector.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
